package com.securepay.security;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterFailureTest {

    @Test
    void databaseFailureReturns503InsteadOfFailingOpen() throws Exception {
        RateLimiter offline = (key, capacity, period) -> {
            throw new DataAccessResourceFailureException("database connection lost");
        };
        RateLimitFilter filter = new RateLimitFilter(offline, new ClientIpResolver(""),
                2, Duration.ofMinutes(1), 2, Duration.ofMinutes(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void missingRewrittenHeaderFromTrustedProxyReturns400() throws Exception {
        RateLimiter unused = (key, capacity, period) -> new RateLimitService.Decision(true, 0);
        RateLimitFilter filter = new RateLimitFilter(unused, new ClientIpResolver("10.0.0.0/8"),
                2, Duration.ofMinutes(1), 2, Duration.ofMinutes(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void spoofedHeaderFromUntrustedClientNeverChangesTheQuotaKey() throws Exception {
        AtomicReference<String> observedKey = new AtomicReference<>();
        RateLimiter recordKey = (key, capacity, period) -> {
            observedKey.set(key);
            return new RateLimitService.Decision(true, 0);
        };
        RateLimitFilter filter = new RateLimitFilter(recordKey, new ClientIpResolver("10.0.0.0/8"),
                2, Duration.ofMinutes(1), 2, Duration.ofMinutes(1));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("203.0.113.77");
        request.addHeader("X-Forwarded-For", "198.51.100.1");
        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(observedKey.get()).isEqualTo("login:ip:203.0.113.77");
    }
}
