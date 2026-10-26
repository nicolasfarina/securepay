package com.securepay.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

public class CorrelationIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlationId";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName() + ".id";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterNestedErrorDispatch(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        doFilterInternal(request, response, chain);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = (String) request.getAttribute(ATTRIBUTE);
        if (id == null) {
            var headers = request.getHeaders(HEADER);
            String candidate = headers != null && headers.hasMoreElements() ? headers.nextElement() : null;
            boolean single = headers == null || !headers.hasMoreElements();
            id = single && candidate != null && SAFE_ID.matcher(candidate).matches()
                    ? candidate : UUID.randomUUID().toString();
            request.setAttribute(ATTRIBUTE, id);
        }
        String previous = MDC.get(MDC_KEY);
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
            if (!request.isAsyncStarted()) {
                log.atInfo().addKeyValue("httpStatus", response.getStatus())
                        .log("HTTP dispatch completed");
            }
        } finally {
            // Restore only our key: other filters may own other MDC entries.
            if (previous == null) {
                MDC.remove(MDC_KEY);
            } else {
                MDC.put(MDC_KEY, previous);
            }
        }
    }
}
