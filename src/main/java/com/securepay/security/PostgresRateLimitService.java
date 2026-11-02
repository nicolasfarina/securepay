package com.securepay.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * PostgreSQL-backed token bucket. Row locks serialize consumption of each identity
 * across application replicas. PostgreSQL clock is authoritative for refills.
 */
@Component
@ConditionalOnProperty(name = "securepay.rate-limit.backend", havingValue = "postgres")
public class PostgresRateLimitService implements RateLimiter {

    private final JdbcTemplate jdbc;

    PostgresRateLimitService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public RateLimitService.Decision tryAcquire(String key, int capacity, Duration refillPeriod) {
        if (key == null || key.isBlank() || capacity < 1 || refillPeriod == null
                || refillPeriod.isNegative() || refillPeriod.isZero() || refillPeriod.toMillis() < 1) {
            throw new IllegalArgumentException("Valid identity, capacity and refill period are required");
        }
        String digest = TokenIdHasher.sha256(key);
        long refillMillis = refillPeriod.toMillis();

        // The upsert locks an existing bucket before SELECT, including against cleanup;
        // a no-op update prevents the row disappearing between statements.
        jdbc.update("""
                INSERT INTO rate_limit_buckets
                    (key_hash, capacity, refill_period_ms, tokens, last_refill_at, last_access_at)
                VALUES (?, ?, ?, ?, clock_timestamp(), clock_timestamp())
                ON CONFLICT (key_hash) DO UPDATE
                    SET last_access_at = rate_limit_buckets.last_access_at
                """, digest, capacity, refillMillis, (double) capacity);

        Bucket state = jdbc.queryForObject("""
                SELECT tokens,
                       GREATEST(0, EXTRACT(EPOCH FROM (clock_timestamp() - last_refill_at)) * 1000)
                           AS elapsed_millis
                FROM rate_limit_buckets
                WHERE key_hash = ?
                FOR UPDATE
                """, (rs, rowNum) -> new Bucket(
                rs.getDouble("tokens"),
                rs.getDouble("elapsed_millis")), digest);

        if (state == null) {
            throw new IllegalStateException("Rate-limit state is missing");
        }
        double available = Math.min(capacity,
                state.tokens() + state.elapsedMillis() * capacity / refillMillis);
        boolean allowed = available >= 1.0;
        double remaining = allowed ? available - 1.0 : available;

        jdbc.update("""
                UPDATE rate_limit_buckets
                SET tokens = ?, capacity = ?, refill_period_ms = ?,
                    last_refill_at = clock_timestamp(), last_access_at = clock_timestamp()
                WHERE key_hash = ?
                """, remaining, capacity, refillMillis, digest);

        if (allowed) {
            return new RateLimitService.Decision(true, 0);
        }
        long retryAfter = (long) Math.ceil((1.0 - available) * refillMillis / capacity / 1000.0);
        return new RateLimitService.Decision(false, Math.max(1L, retryAfter));
    }

    /**
     * Rows idle for at least twice their refill period are fully replenished;
     * deleting them does not reset an active client quota.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void removeIdleBuckets() {
        jdbc.update("""
                DELETE FROM rate_limit_buckets
                WHERE last_access_at < clock_timestamp()
                      - (refill_period_ms * INTERVAL '2 milliseconds')
                """);
    }

    private record Bucket(double tokens, double elapsedMillis) {}
}
