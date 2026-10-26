package com.securepay.observability;

import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "management.tracing.sampling.probability=1.0",
        "management.opentelemetry.tracing.export.otlp.enabled=false"
})
@AutoConfigureTracing(export = false)
@AutoConfigureMockMvc
@Import(TracePropagationHttpTest.ProbeConfig.class)
class TracePropagationHttpTest {
    private static final String PARENT_TRACE_ID = "0123456789abcdef0123456789abcdef";
    private static final String PARENT_SPAN_ID = "0123456789abcdef";
    private static final String PARENT_TRACEPARENT = "00-" + PARENT_TRACE_ID + "-" + PARENT_SPAN_ID + "-01";
    private static final AtomicReference<String> downstreamTraceparent = new AtomicReference<>();
    private static HttpServer downstream;
    private static final TextMapGetter<HttpServletRequest> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest request) {
            return java.util.Collections.list(request.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest request, String key) {
            return request.getHeader(key);
        }
    };

    @Autowired private MockMvc mvc;
    @AfterEach
    void resetTelemetry() {
        downstreamTraceparent.set(null);
    }

    @AfterAll
    static void stopDownstream() {
        if (downstream != null) downstream.stop(0);
    }

    @Test
    void incomingW3cContextContinuesToOutgoingHttpService() throws Exception {
        mvc.perform(get("/day39/propagate")
                        .with(user("customer-123").roles("CUSTOMER"))
                        .header("traceparent", PARENT_TRACEPARENT))
                .andExpect(status().isOk())
                .andExpect(content().string("downstream-ok"));

        String propagated = downstreamTraceparent.get();
        assertThat(propagated).matches("00-" + PARENT_TRACE_ID + "-[0-9a-f]{16}-01");
        assertThat(propagated.substring(36, 52)).isEqualTo(PARENT_SPAN_ID);
    }

    private static synchronized String startDownstream() {
        if (downstream != null) return "http://127.0.0.1:" + downstream.getAddress().getPort();
        try {
            downstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            downstream.createContext("/probe", exchange -> {
                downstreamTraceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
                byte[] body = "downstream-ok".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
            downstream.start();
            return "http://127.0.0.1:" + downstream.getAddress().getPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not start the loopback trace propagation probe", exception);
        }
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        TraceProbeController traceProbeController(OpenTelemetryRestClientCustomizer customizer,
                @Value("${day39.downstream-url}") String downstreamUrl) {
            RestClient.Builder builder = RestClient.builder();
            customizer.customize(builder);
            return new TraceProbeController(builder.baseUrl(downstreamUrl).build());
        }

        @Bean
        static org.springframework.test.context.DynamicPropertyRegistrar downstreamUrl() {
            return registry -> registry.add("day39.downstream-url", TracePropagationHttpTest::startDownstream);
        }
    }

    @RestController
    static class TraceProbeController {
        private final RestClient downstream;

        TraceProbeController(RestClient downstream) {
            this.downstream = downstream;
        }

        @GetMapping("/day39/propagate")
        String propagate(HttpServletRequest request) {
            Context parent = W3CTraceContextPropagator.getInstance()
                    .extract(Context.root(), request, GETTER);
            try (var scope = parent.makeCurrent()) {
                return downstream.get().uri("/probe").retrieve().body(String.class);
            }
        }
    }
}
