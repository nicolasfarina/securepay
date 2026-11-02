package com.securepay.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Day44ResiliencePatternsTest {

    @Test
    void retryRepeatsOnlyTheConfiguredTransientFailure() {
        Retry retry = Retry.of("day44-retry", RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ZERO)
                .retryOnException(failure -> failure instanceof TransientDependencyFailure)
                .build());
        AtomicInteger attempts = new AtomicInteger();
        Supplier<String> call = Retry.decorateSupplier(retry, () -> {
            if (attempts.incrementAndGet() < 3) throw new TransientDependencyFailure();
            return "available";
        });

        assertThat(call.get()).isEqualTo("available");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void circuitBreakerStopsCallingAnUnhealthyDependency() {
        CircuitBreaker circuitBreaker = CircuitBreaker.of("day44-circuit", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .build());
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> call = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            calls.incrementAndGet();
            throw new TransientDependencyFailure();
        });

        assertThatThrownBy(call::get).isInstanceOf(TransientDependencyFailure.class);
        assertThatThrownBy(call::get).isInstanceOf(TransientDependencyFailure.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(call::get).isInstanceOf(CallNotPermittedException.class);
        assertThat(calls).hasValue(2);
    }

    @Test
    void bulkheadRejectsWorkWhenItsOnlyPermitIsOccupied() throws Exception {
        var bulkhead = Bulkhead.of("day44-bulkhead", BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ZERO)
                .build());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<String> call = Bulkhead.decorateSupplier(bulkhead, () -> {
            entered.countDown();
            await(release);
            return "finished";
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> first = executor.submit(call::get);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(call::get).isInstanceOf(BulkheadFullException.class);
            release.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("finished");
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void timeLimiterBoundsTheWaitForAnAsynchronousDependency() {
        TimeLimiter timeLimiter = TimeLimiter.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofMillis(50))
                .cancelRunningFuture(true)
                .build());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            assertThatThrownBy(() -> timeLimiter.executeFutureSupplier(() ->
                    CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(5_000);
                            return "too late";
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("interrupted", exception);
                        }
                    }, executor)))
                    .isInstanceOf(TimeoutException.class);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static final class TransientDependencyFailure extends RuntimeException {
    }
}
