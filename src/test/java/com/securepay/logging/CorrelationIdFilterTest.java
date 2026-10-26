package com.securepay.logging;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.securepay.logging.CorrelationIdFilter.HEADER;
import static com.securepay.logging.CorrelationIdFilter.MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"client_123-ABC", "a", "123e4567-e89b-12d3-a456-426614174000"})
    void keepsValidHeaderInsideChainAndResponse(String id) throws Exception {
        assertRequest(id, id);
    }

    @Test
    void acceptsExactly64Characters() throws Exception {
        assertRequest("a".repeat(64), "a".repeat(64));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "id\r\nFORGED EVENT", "id\nFORGED EVENT", "id\tvalue", "id/value",
            "id,value", "á", "a b"})
    void replacesAbsentOrUnsafeHeaderWithUuid(String candidate) throws Exception {
        assertRequest(candidate, null);
    }

    @Test
    void replacesOverlongHeader() throws Exception {
        assertRequest("a".repeat(65), null);
    }

    @Test
    void rejectsAmbiguousDuplicateHeaders() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(HEADER, "first");
        request.addHeader(HEADER, "second");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get(MDC_KEY)).isNotIn("first", "second"));
        UUID.fromString(response.getHeader(HEADER));
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void restoresPreviousKeyAndPreservesOtherMdcEntries() throws Exception {
        MDC.put(MDC_KEY, "outer");
        MDC.put("other", "preserved");
        assertRequest("inner", "inner");
        assertThat(MDC.get(MDC_KEY)).isEqualTo("outer");
        assertThat(MDC.get("other")).isEqualTo("preserved");
    }

    @Test
    void cleansMdcAndPropagatesOriginalExceptions() {
        for (Exception failure : new Exception[]{new ServletException("test"), new IOException("test"),
                new IllegalStateException("test")}) {
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), response, (req, res) -> {
                assertThat(MDC.get(MDC_KEY)).isEqualTo(response.getHeader(HEADER));
                if (failure instanceof ServletException servlet) throw servlet;
                if (failure instanceof IOException io) throw io;
                throw (RuntimeException) failure;
            })).isSameAs(failure);
            assertThat(MDC.get(MDC_KEY)).isNull();
        }
    }

    @Test
    void restoresOuterContextOnException() {
        MDC.put(MDC_KEY, "outer");
        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (req, res) -> { throw new ServletException("test"); })).isInstanceOf(ServletException.class);
        assertThat(MDC.get(MDC_KEY)).isEqualTo("outer");
    }

    @Test
    void reusesIdOnAsyncAndErrorRedispatch() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        String id = response.getHeader(HEADER);
        for (DispatcherType type : new DispatcherType[]{DispatcherType.ASYNC, DispatcherType.ERROR}) {
            request.setDispatcherType(type);
            if (type == DispatcherType.ERROR) request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test");
            var dispatchedResponse = new MockHttpServletResponse();
            filter.doFilter(request, dispatchedResponse, (req, res) -> assertThat(MDC.get(MDC_KEY)).isEqualTo(id));
            assertThat(dispatchedResponse.getHeader(HEADER)).isEqualTo(id);
            assertThat(MDC.get(MDC_KEY)).isNull();
        }
    }

    @Test
    void nestedErrorDispatchRetainsContextUntilOuterChainFinishes() throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(HEADER, "nested-error");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            request.setDispatcherType(DispatcherType.ERROR);
            request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/test");
            filter.doFilter(request, response, (nestedRequest, nestedResponse) ->
                    assertThat(MDC.get(MDC_KEY)).isEqualTo("nested-error"));
            assertThat(MDC.get(MDC_KEY)).isEqualTo("nested-error");
        });
        assertThat(MDC.get(MDC_KEY)).isNull();
    }

    @Test
    void concurrentRequestsStayIsolatedAndReusedWorkersAreClean() throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> concurrentRequest("first", barrier));
            var second = pool.submit(() -> concurrentRequest("second", barrier));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> { assertRequest("old", "old"); return null; }).get(10, TimeUnit.SECONDS);
            pool.submit(() -> {
                assertThat(MDC.get(MDC_KEY)).isNull();
                assertRequest(null, null);
                return null;
            }).get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void unmanagedAsyncWorkerDoesNotAutomaticallyInheritMdc() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> {
                assertThat(MDC.get(MDC_KEY)).isNotBlank();
                try {
                    assertThat(pool.submit(() -> MDC.get(MDC_KEY)).get(5, TimeUnit.SECONDS)).isNull();
                } catch (Exception exception) {
                    throw new ServletException(exception);
                }
            });
        }
    }

    private Void concurrentRequest(String id, CyclicBarrier barrier) throws Exception {
        var request = new MockHttpServletRequest();
        request.addHeader(HEADER, id);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                assertThat(MDC.get(MDC_KEY)).isEqualTo(id);
            } catch (Exception exception) {
                throw new ServletException(exception);
            }
        });
        assertThat(MDC.get(MDC_KEY)).isNull();
        return null;
    }

    private void assertRequest(String input, String expected) throws Exception {
        String before = MDC.get(MDC_KEY);
        var request = new MockHttpServletRequest();
        if (input != null) request.addHeader(HEADER, input);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            String id = response.getHeader(HEADER);
            assertThat(MDC.get(MDC_KEY)).isEqualTo(id);
            if (expected != null) assertThat(id).isEqualTo(expected);
            else assertThat(UUID.fromString(id).toString()).isEqualTo(id);
        });
        assertThat(MDC.get(MDC_KEY)).isEqualTo(before);
    }
}
