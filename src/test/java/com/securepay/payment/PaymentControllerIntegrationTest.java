package com.securepay.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import com.securepay.auth.domain.UserAccount;
import com.securepay.auth.domain.UserRole;
import com.securepay.security.JwtService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    private String customer123;
    private String customer789;
    private String customer123Token;
    private String customer789Token;

    @BeforeEach
    void registerCustomers() throws Exception {
        customer123 = "customer-123-" + UUID.randomUUID();
        customer789 = "customer-789-" + UUID.randomUUID();
        customer123Token = register(customer123);
        customer789Token = register(customer789);
    }

    @Test
    void createsAPendingPayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "create-payment-001")
                        .header("Authorization", "Bearer " + customer123Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value(customer123))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void returnsConflictWhenAKeyIsReusedForDifferentPaymentData() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .header("Authorization", "Bearer " + customer123Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "conflicting-payment-001")
                        .header("Authorization", "Bearer " + customer123Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("20.00")))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsRequestsWithoutAnIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Authorization", "Bearer " + customer123Token)
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
                        .header("Authorization", "Bearer " + customer123Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentJson("10.00")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/payments/{paymentId}", paymentId)
                        .header("Authorization", "Bearer " + customer789Token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPaymentData() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .header("Idempotency-Key", "invalid-payment-001")
                        .header("Authorization", "Bearer " + customer123Token)
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

    @Test
    void rejectsMalformedBearerToken() throws Exception {
        mockMvc.perform(get("/api/payments/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void customerCannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + customer123Token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRolePassesAdminRouteAuthorization() throws Exception {
        String adminToken = jwtService.issueToken(new UserAccount(
                UUID.randomUUID(), "admin-" + UUID.randomUUID(), "not-used", UserRole.ADMIN));
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String register(String username) throws Exception {
        String response = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"correct horse battery staple"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.token");
    }
}
