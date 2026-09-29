package com.securepay.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsAPendingPayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "create-payment-001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void returnsConflictWhenAKeyIsReusedForDifferentPaymentData() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("20.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRequestsWithoutAnIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "unauthenticated-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void hidesPaymentsFromAnotherCustomer() throws Exception {
        String response = mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "customer-owned-payment-001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-789", "local-development-only")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPaymentData() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "invalid-payment-001")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("customer-123", "local-development-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("0.00")))
                .andExpect(status().isBadRequest());
    }

    private String paymentJson(String amount) {
        return """
                {
                  "merchantId": "merchant-456",
                  "amount": %s,
                  "currency": "USD"
                }
                """.formatted(amount);
    }
}
