package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import com.jayway.jsonpath.JsonPath;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "securepay.rate-limit.login.max-attempts=2",
        "securepay.rate-limit.login.window=1h",
        "securepay.rate-limit.payments.max-requests=1",
        "securepay.rate-limit.payments.window=1h"
})
@AutoConfigureMockMvc
class RateLimitFilterIntegrationTest {

    private static final String PAYMENT = """
            {"merchantId":"merchant-1","amount":10.00,"currency":"USD"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksFurtherLoginAttemptsAfterConfiguredFailedAttemptsFromAnIp() throws Exception {
        login("customer-123", "wrong", "203.0.113.10")
                .andExpect(status().isUnauthorized());
        login("customer-123", "wrong", "203.0.113.10")
                .andExpect(status().isUnauthorized());

        MvcResult limited = login("customer-123", "local-development-only", "203.0.113.10")
                .andExpect(status().isTooManyRequests())
                .andReturn();

        assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void limitsPaymentCreationPerAuthenticatedCustomerButAllowsReadsAndOtherCustomers() throws Exception {
        String customerOne = token("customer-123");
        String customerTwo = token("customer-789");

        MvcResult created = createPayment(customerOne, "limit-one").andExpect(status().isCreated()).andReturn();
        String paymentId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        createPayment(customerOne, "limit-two").andExpect(status().isTooManyRequests());
        createPayment(customerTwo, "limit-three").andExpect(status().isCreated());
        mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", "Bearer " + customerOne))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions login(String username, String password, String ip)
            throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(remoteAddress(ip))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}
                        """.formatted(username, password)));
    }

    private String token(String username) throws Exception {
        String body = login(username, "local-development-only", "203.0.113.20")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private org.springframework.test.web.servlet.ResultActions createPayment(String token, String idempotencyKey)
            throws Exception {
        return mockMvc.perform(post("/api/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PAYMENT));
    }

    private static RequestPostProcessor remoteAddress(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
