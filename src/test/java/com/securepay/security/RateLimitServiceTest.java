package com.securepay.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitServiceTest {

    @Test
    void refillsTokensGraduallyAndReportsWhenTheNextTokenIsAvailable() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        RateLimitService service = new RateLimitService(10, clock);

        assertThat(service.tryAcquire("customer", 2, Duration.ofSeconds(10)).allowed()).isTrue();
        assertThat(service.tryAcquire("customer", 2, Duration.ofSeconds(10)).allowed()).isTrue();

        RateLimitService.Decision limited = service.tryAcquire("customer", 2, Duration.ofSeconds(10));
        assertThat(limited.allowed()).isFalse();
        assertThat(limited.retryAfterSeconds()).isEqualTo(5);

        clock.advance(Duration.ofSeconds(5));
        assertThat(service.tryAcquire("customer", 2, Duration.ofSeconds(10)).allowed()).isTrue();
    }

    @Test
    void boundsTrackedClientsAndReusesSpaceAfterAnIdleWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-23T12:00:00Z"));
        RateLimitService service = new RateLimitService(1, clock);

        assertThat(service.tryAcquire("first", 1, Duration.ofSeconds(10)).allowed()).isTrue();
        RateLimitService.Decision full = service.tryAcquire("second", 1, Duration.ofSeconds(10));
        assertThat(full.allowed()).isFalse();

        clock.advance(Duration.ofSeconds(10));
        assertThat(service.tryAcquire("second", 1, Duration.ofSeconds(10)).allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
