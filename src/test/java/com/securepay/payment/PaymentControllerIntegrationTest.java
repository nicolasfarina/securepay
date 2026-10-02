package com.securepay.payment;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                        .header("Authorization", bearer("customer-123"))
                        .header("Idempotency-Key", "create-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("customer-123"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void returnsConflictWhenAKeyIsReusedForDifferentPaymentData() throws Exception {
        String authorization = bearer("customer-123");

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .header("Authorization", authorization)
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("20.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRequestsWithoutAnIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", bearer("customer-123"))
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
                        .header("Authorization", bearer("customer-123"))
                        .header("Idempotency-Key", "customer-owned-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("customer-789")))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPaymentData() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", bearer("customer-123"))
                        .header("Idempotency-Key", "invalid-payment-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("0.00")))
                .andExpect(status().isBadRequest());
    }

    private String bearer(String username) throws Exception {
        String response = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "local-development-only")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = JsonPath.read(response, "$.accessToken");
        return "Bearer " + token;
    }

    private String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
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
