package com.securepay.security;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles({"local", "monitoring"})
@AutoConfigureMockMvc
@AutoConfigureMetrics(export = true)
class PrometheusSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired MeterRegistry registry;

    @Test
    void scrapeRequiresAdminAndIncludesTheLatencyObjectiveBucket() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus").with(user("customer").roles("CUSTOMER")))
                .andExpect(status().isForbidden());

        registry.timer("http.server.requests", "uri", "/api/payments", "status", "200")
                .record(Duration.ofMillis(300));
        String scrape = mvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(scrape).contains("http_server_requests_seconds_bucket", "le=\"0.5\"");
    }
}
