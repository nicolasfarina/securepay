package com.securepay.security;

import com.securepay.support.PostgresTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in only: runs on the disposable securepay_test PostgreSQL database. */
@SpringBootTest(properties = "securepay.rate-limit.backend=postgres")
@EnabledIfEnvironmentVariable(named = "SECUREPAY_POSTGRES_TEST", matches = "true")
class PostgresRateLimitServiceTest {

    @Autowired RateLimiter limiter;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry properties) {
        PostgresTestDatabase.configure(properties);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM rate_limit_buckets");
    }

    @Test
    void manyConcurrentConsumersCannotExceedSharedQuota() throws Exception {
        int contenders = 12;
        CyclicBarrier start = new CyclicBarrier(contenders);
        var requests = new ArrayList<Callable<Boolean>>();
        for (int i = 0; i < contenders; i++) {
            requests.add(() -> {
                start.await(15, TimeUnit.SECONDS);
                return limiter.tryAcquire("login:ip:198.51.100.99", 3, Duration.ofHours(1)).allowed();
            });
        }
        try (var executor = Executors.newFixedThreadPool(contenders)) {
            var results = executor.invokeAll(requests, 45, TimeUnit.SECONDS);
            int permits = 0;
            for (var result : results) {
                if (result.get()) permits++;
            }
            assertThat(permits).isEqualTo(3);
        }
    }

    @Test
    void persistsOnlyHashedKeysAndIsolatesDifferentUsers() {
        assertThat(limiter.tryAcquire("payment:user:customer-123", 1, Duration.ofHours(1)).allowed())
                .isTrue();
        assertThat(limiter.tryAcquire("payment:user:customer-123", 1, Duration.ofHours(1)).allowed())
                .isFalse();
        assertThat(limiter.tryAcquire("payment:user:customer-789", 1, Duration.ofHours(1)).allowed())
                .isTrue();

        var hashes = jdbc.queryForList("SELECT key_hash FROM rate_limit_buckets", String.class);
        assertThat(hashes).hasSize(2).allSatisfy(hash ->
                assertThat(hash).matches("[a-f0-9]{64}"));
        assertThat(hashes).doesNotContain("payment:user:customer-123");
    }

    @Test
    void exposesPositiveRetryAfterWhenSharedBucketIsExhausted() {
        assertThat(limiter.tryAcquire("login:ip:203.0.113.199", 1, Duration.ofMinutes(1)).allowed())
                .isTrue();
        RateLimitService.Decision denied = limiter.tryAcquire(
                "login:ip:203.0.113.199", 1, Duration.ofMinutes(1));
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L, 60L);
    }
}
