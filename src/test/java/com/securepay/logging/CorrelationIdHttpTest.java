package com.securepay.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.securepay.payment.CreatePaymentRequest;
import com.securepay.payment.Payment;
import com.securepay.payment.PaymentService;
import com.securepay.payment.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import static com.securepay.logging.CorrelationIdFilter.HEADER;
import static com.securepay.logging.CorrelationIdFilter.MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "securepay.rate-limit.login.max-attempts=1")
@AutoConfigureMockMvc
@Import(CorrelationIdHttpTest.ProbeConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdHttpTest {
    @Autowired private MockMvc mvc;
    @Autowired private PaymentService paymentService;

    @AfterEach
    void noContextLeak() {
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void validIdReachesControllerAndStructuredJsonLog(CapturedOutput output) throws Exception {
        mvc.perform(get("/day36/probe").with(user("customer-123")).header(HEADER, "http-valid_36"))
                .andExpect(status().isOk()).andExpect(header().string(HEADER, "http-valid_36"))
                .andExpect(content().string("http-valid_36"));
        assertThat(output.getOut()).contains("\"correlationId\":\"http-valid_36\"", "\"message\":\"day36 probe\"");
    }

    @Test
    void absentAndInjectedIdsAreReplacedBeforeLogging(CapturedOutput output) throws Exception {
        var absent = mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andReturn();
        String generated = absent.getResponse().getHeader(HEADER);
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
        var injected = mvc.perform(get("/day36/probe").with(user("customer-123"))
                        .header(HEADER, "unsafe\r\nFORGED_DAY36_EVENT"))
                .andExpect(status().isOk()).andReturn();
        String safe = injected.getResponse().getHeader(HEADER);
        assertThat(UUID.fromString(safe).toString()).isEqualTo(safe).isNotEqualTo(generated);
        assertThat(injected.getResponse().getContentAsString()).isEqualTo(safe);
        assertThat(output.getOut()).doesNotContain("FORGED_DAY36_EVENT");
    }

    @Test
    void securityRejectionsAndHandledExceptionsKeepId() throws Exception {
        mvc.perform(get("/api/payments/missing").header(HEADER, "unauthorized-36"))
                .andExpect(status().isUnauthorized()).andExpect(header().string(HEADER, "unauthorized-36"));
        mvc.perform(get("/api/admin/payments").with(user("customer-123").roles("CUSTOMER"))
                        .header(HEADER, "forbidden-36"))
                .andExpect(status().isForbidden()).andExpect(header().string(HEADER, "forbidden-36"));
        mvc.perform(get("/api/payments/{id}", UUID.randomUUID()).with(user("customer-123").roles("CUSTOMER"))
                        .header(HEADER, "missing-36"))
                .andExpect(status().isNotFound()).andExpect(header().string(HEADER, "missing-36"));
        mvc.perform(get("/day36/conflict").with(user("customer-123")).header(HEADER, "conflict-36"))
                .andExpect(status().isConflict()).andExpect(header().string(HEADER, "conflict-36"));
    }

    @Test
    void rateLimitShortCircuitKeepsId() throws Exception {
        for (int attempt = 0; attempt < 2; attempt++) {
            var result = mvc.perform(post("/auth/login").with(request -> {
                        request.setRemoteAddr("203.0.113.136");
                        return request;
                    }).contentType("application/json").content("{}")
                    .header(HEADER, "limited-36"));
            result.andExpect(status().is(attempt == 0 ? 400 : 429))
                    .andExpect(header().string(HEADER, "limited-36"));
        }
    }

    @Test
    void metricsAreRestrictedToAdminsAndExposeOnlyBoundedPaymentStatusTags() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").with(user("customer-123").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        Payment payment = paymentService.create(
                new CreatePaymentRequest("merchant-metrics", new BigDecimal("12.00"), "USD"),
                "customer-123", "metrics-" + UUID.randomUUID());
        paymentService.transition(payment.id(), PaymentStatus.APPROVED);

        mvc.perform(get("/actuator/metrics").with(user("admin-123").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.names").isArray())
                .andExpect(jsonPath("$.names").value(org.hamcrest.Matchers.hasItem(
                        "securepay.payment.status.transitions")));
        mvc.perform(get("/actuator/metrics/securepay.payment.status.transitions")
                        .param("tag", "from:PENDING").param("tag", "to:APPROVED")
                        .with(user("admin-123").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("securepay.payment.status.transitions"))
                .andExpect(jsonPath("$.measurements[0].value")
                        .value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    void unhandledExceptionDoesNotLeakMdc() {
        assertThatThrownBy(() -> mvc.perform(get("/day36/failure").with(user("customer-123"))
                .header(HEADER, "failed-36"))).hasRootCauseInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void asyncRedispatchKeepsResponseId() throws Exception {
        var initial = mvc.perform(get("/day36/async").with(user("customer-123"))
                        .header(HEADER, "async-36"))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(MDC.get(MDC_KEY)).isNull();
        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(header().string(HEADER, "async-36")).andExpect(content().string("async-36"));
    }

    @Test
    void completableFutureKeepsMdcWhenGivenSpringExecutor() throws Exception {
        var initial = mvc.perform(get("/day36/future").with(user("customer-123"))
                        .header(HEADER, "future-36"))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk())
                .andExpect(header().string(HEADER, "future-36")).andExpect(content().string("future-36"));
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean ProbeController probeController(
                @Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor) {
            return new ProbeController(applicationTaskExecutor);
        }
    }

    @RestController
    static class ProbeController {
        private final AsyncTaskExecutor applicationTaskExecutor;

        ProbeController(@Qualifier("applicationTaskExecutor") AsyncTaskExecutor applicationTaskExecutor) {
            this.applicationTaskExecutor = applicationTaskExecutor;
        }

        @GetMapping("/day36/probe")
        String probe() {
            LoggerFactory.getLogger(ProbeController.class).info("day36 probe");
            return MDC.get(MDC_KEY);
        }

        @GetMapping("/day36/conflict")
        String conflict() { throw new IllegalStateException("test conflict"); }

        @GetMapping("/day36/failure")
        String failure() { throw new UnsupportedOperationException("test failure"); }

        @GetMapping("/day36/async")
        Callable<String> async() { return () -> MDC.get(MDC_KEY); }

        @GetMapping("/day36/future")
        CompletableFuture<String> future() {
            return CompletableFuture.supplyAsync(() -> MDC.get(MDC_KEY), applicationTaskExecutor);
        }
    }
}
