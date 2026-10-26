package com.securepay.logging;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MdcTaskDecoratorTest {
    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void clearCallingThreadMdc() {
        MDC.clear();
    }

    @Test
    void copiesSubmittingContextAndRestoresWorkersPreviousContext() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                MDC.put("worker", "preserve-me");
                return null;
            }).get();

            MDC.put("correlationId", "request-123");
            MDC.put("actor", "customer-123");
            Future<?> task = executor.submit(decorator.decorate(() -> {
                assertThat(MDC.get("correlationId")).isEqualTo("request-123");
                assertThat(MDC.get("actor")).isEqualTo("customer-123");
                assertThat(MDC.get("worker")).isNull();
            }));
            task.get();

            var restored = executor.submit(() -> MDC.getCopyOfContextMap()).get();
            assertThat(restored).containsExactly(java.util.Map.entry("worker", "preserve-me"));
            assertThat(MDC.get("correlationId")).isEqualTo("request-123");
        }
    }

    @Test
    void restoresWorkerContextWhenTaskThrows() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                MDC.put("worker", "preserve-on-failure");
                return null;
            }).get();
            MDC.put("correlationId", "failed-request");

            var failed = executor.submit(decorator.decorate(() -> {
                assertThat(MDC.get("correlationId")).isEqualTo("failed-request");
                throw new IllegalStateException("expected failure");
            }));
            assertThatThrownBy(failed::get)
                    .hasCauseInstanceOf(IllegalStateException.class)
                    .cause().hasMessage("expected failure");
            assertThat(executor.submit(() -> MDC.get("worker")).get()).isEqualTo("preserve-on-failure");
            assertThat(executor.submit(() -> MDC.get("correlationId")).get()).isNull();
        }
    }

    @Test
    void clearsStaleWorkerContextWhenSubmittingThreadHasNoContext() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                MDC.put("stale", "previous-request");
                return null;
            }).get();

            executor.submit(decorator.decorate(() -> {
                assertThat(MDC.getCopyOfContextMap()).isNull();
            })).get();

            assertThat(executor.submit(() -> MDC.get("stale")).get()).isEqualTo("previous-request");
        }
    }

    @Test
    void propagatesTraceContextAndRestoresWorkersPreviousTraceContext() throws Exception {
        var submittedSpanContext = SpanContext.create(
                "0123456789abcdef0123456789abcdef",
                "0123456789abcdef",
                TraceFlags.getSampled(),
                TraceState.getDefault());
        var submittedContext = Span.wrap(submittedSpanContext).storeInContext(Context.root());
        var observedTraceId = new AtomicReference<String>();

        try (var executor = Executors.newSingleThreadExecutor();
                var scope = submittedContext.makeCurrent()) {
            executor.submit(decorator.decorate(
                    () -> observedTraceId.set(Span.current().getSpanContext().getTraceId()))).get();

            assertThat(observedTraceId).hasValue(submittedSpanContext.getTraceId());
            assertThat(executor.submit(() -> Span.current().getSpanContext().isValid()).get()).isFalse();
        }
    }
}
