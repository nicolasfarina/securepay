package com.securepay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimitService;
    private final ClientIpResolver clientIpResolver;
    private final int loginMaxAttempts;
    private final Duration loginWindow;
    private final int paymentMaxRequests;
    private final Duration paymentWindow;

    public RateLimitFilter(
            RateLimiter rateLimitService,
            ClientIpResolver clientIpResolver,
            @Value("${securepay.rate-limit.login.max-attempts:30}") int loginMaxAttempts,
            @Value("${securepay.rate-limit.login.window:1m}") Duration loginWindow,
            @Value("${securepay.rate-limit.payments.max-requests:20}") int paymentMaxRequests,
            @Value("${securepay.rate-limit.payments.window:1m}") Duration paymentWindow) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
        this.loginMaxAttempts = loginMaxAttempts;
        this.loginWindow = loginWindow;
        this.paymentMaxRequests = paymentMaxRequests;
        this.paymentWindow = paymentWindow;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        boolean login = "POST".equals(request.getMethod()) && matches(path, "/auth/login");
        boolean createPayment = "POST".equals(request.getMethod()) && matches(path, "/api/payments");
        return !login && !createPayment;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        final String key;
        final int capacity;
        final Duration window;
        try {
            if (isLogin(request)) {
                key = "login:ip:" + clientIpResolver.resolve(request);
                capacity = loginMaxAttempts;
                window = loginWindow;
            } else {
                key = paymentKey(request);
                capacity = paymentMaxRequests;
                window = paymentWindow;
            }
        } catch (ClientIpResolver.InvalidForwardedAddressException ex) {
            response.sendError(HttpStatus.BAD_REQUEST.value());
            return;
        }

        final RateLimitService.Decision decision;
        try {
            decision = rateLimitService.tryAcquire(key, capacity, window);
        } catch (DataAccessException | TransactionException ex) {
            // Deny requests when the shared quota store fails; never bypass the limit.
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value());
            return;
        }

        if (!decision.allowed()) {
            reject(response, decision);
            return;
        }

        // Keep downstream exception handling outside the limiter's catch clauses.
        filterChain.doFilter(request, response);
    }

    private String paymentKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "payment:user:" + authentication.getName();
        }
        return "payment:ip:" + clientIpResolver.resolve(request);
    }

    private static void reject(HttpServletResponse response, RateLimitService.Decision decision)
            throws IOException {
        response.setHeader("Retry-After", Long.toString(decision.retryAfterSeconds()));
        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private static boolean isLogin(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && matches(requestPath(request), "/auth/login");
    }

    private static String requestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }

    private static boolean matches(String requestPath, String expectedPath) {
        return expectedPath.equals(requestPath) || (expectedPath + "/").equals(requestPath);
    }

}
