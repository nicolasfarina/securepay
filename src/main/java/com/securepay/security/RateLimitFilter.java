package com.securepay.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final int loginMaxAttempts;
    private final Duration loginWindow;
    private final int paymentMaxRequests;
    private final Duration paymentWindow;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            @Value("${securepay.rate-limit.login.max-attempts:30}") int loginMaxAttempts,
            @Value("${securepay.rate-limit.login.window:1m}") Duration loginWindow,
            @Value("${securepay.rate-limit.payments.max-requests:20}") int paymentMaxRequests,
            @Value("${securepay.rate-limit.payments.window:1m}") Duration paymentWindow) {
        this.rateLimitService = rateLimitService;
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
        if (isLogin(request)) {
            limitLoginFailures(request, response, filterChain);
            return;
        }

        String key = paymentKey(request);
        RateLimitService.Decision decision = rateLimitService.tryAcquire(
                key, paymentMaxRequests, paymentWindow);
        if (!decision.allowed()) {
            reject(response, decision);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void limitLoginFailures(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String key = "login:ip:" + clientIp(request);
        RateLimitService.Decision decision = rateLimitService.tryAcquire(key, loginMaxAttempts, loginWindow);
        if (!decision.allowed()) {
            reject(response, decision);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String paymentKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "payment:user:" + authentication.getName();
        }
        return "payment:ip:" + clientIp(request);
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

    private static String clientIp(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        return remoteAddress == null || remoteAddress.isBlank() ? "unknown" : remoteAddress;
    }
}
