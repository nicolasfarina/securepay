package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureMetrics(export = true)
class PrometheusDefaultExposureTest {
    @Autowired MockMvc mvc;

    @Test
    void scrapeIsNotExposedByDefaultEvenToAdmin() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
