package com.securepay.security;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Day 35: negative security requests against the real authentication and rate-limit filters.
 * Explicit remote addresses keep these tests independent of other rate-limiting cases.
 */
@SpringBootTest(properties = {
        "securepay.rate-limit.login.max-attempts=2",
        "securepay.rate-limit.login.window=1h",
        "securepay.rate-limit.payments.max-requests=20",
        "securepay.rate-limit.payments.window=1h"
})
@AutoConfigureMockMvc
class ApiNegativeSecurityTest {

    private static final String WRONG_CREDENTIALS = """
            {"username":"customer-123","password":"not-the-password"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void spoofedForwardedForCannotBypassLoginRateLimit() throws Exception {
        String remoteIp = "198.51.100.50";

        for (int attempt = 0; attempt < 2; attempt++) {
            loginAttempt(remoteIp, "203.0.113." + (attempt + 10))
                    .andExpect(status().isUnauthorized());
        }

        loginAttempt(remoteIp, "203.0.113.99")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void spoofedIdentityHeadersCannotPromoteCustomerToAdmin() throws Exception {
        String token = customerToken();

        mockMvc.perform(get("/api/admin/payments")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Role", "ADMIN")
                        .header("X-Authenticated-User", "admin-123"))
                .andExpect(status().isForbidden());
    }

    @Test
    void spoofedIdentityHeadersCannotAuthenticateWithoutJwt() throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .header("X-Role", "ADMIN")
                        .header("X-Authenticated-User", "admin-123"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Basic YWRtaW46YWRtaW4=",
            "Token arbitrary",
            "Bearer invalid.jwt.token"
    })
    void unsupportedOrInvalidAuthorizationHeadersAreRejected(String authorization)
            throws Exception {
        mockMvc.perform(get("/api/admin/payments")
                        .header("Authorization", authorization))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions loginAttempt(String remoteIp, String forwardedFor) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .with(remoteAddress(remoteIp))
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(WRONG_CREDENTIALS));
    }

    private String customerToken() throws Exception {
        String body = mockMvc.perform(post("/auth/login")
                        .with(remoteAddress("198.51.100.51"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"customer-123","password":"local-development-only"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static RequestPostProcessor remoteAddress(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
