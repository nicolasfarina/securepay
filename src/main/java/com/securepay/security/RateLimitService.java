package com.securepay.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/** A bounded, per-process token bucket for the learning project's HTTP limits. */
@Component
public class RateLimitService {

    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Semaphore entrySlots;
    private final Object entryLock = new Object();
    private final Clock clock;

    @Autowired
    public RateLimitService(
            @Value("${securepay.rate-limit.max-tracked-clients:10000}") int maxTrackedClients) {
        this(maxTrackedClients, Clock.systemUTC());
    }

    RateLimitService(int maxTrackedClients, Clock clock) {
        if (maxTrackedClients < 1) {
            throw new IllegalArgumentException("maxTrackedClients must be positive");
        }
        this.entrySlots = new Semaphore(maxTrackedClients);
        this.clock = clock;
    }

    public Decision check(String key, int capacity, Duration refillPeriod) {
        validate(capacity, refillPeriod);
        TokenBucket bucket = buckets.get(key);
        return bucket == null ? Decision.permit() : bucket.check(clock.millis(), false);
    }

    public Decision tryAcquire(String key, int capacity, Duration refillPeriod) {
        validate(capacity, refillPeriod);
        long now = clock.millis();
        TokenBucket bucket = buckets.get(key);
        if (bucket == null) {
            bucket = createBucketIfPossible(key, capacity, refillPeriod, now);
            if (bucket == null) {
                return Decision.rejected(Math.max(1, (refillPeriod.toMillis() + 999) / 1000));
            }
        }
        return bucket.check(now, true);
    }

    private TokenBucket createBucketIfPossible(String key, int capacity, Duration refillPeriod, long now) {
        synchronized (entryLock) {
            TokenBucket existing = buckets.get(key);
            if (existing != null) {
                return existing;
            }

            evictExpired(now);
            if (!entrySlots.tryAcquire()) {
                return null;
            }

            TokenBucket created = new TokenBucket(capacity, refillPeriod.toMillis(), now);
            buckets.put(key, created);
            return created;
        }
    }

    private void evictExpired(long now) {
        buckets.forEach((key, bucket) -> {
            if (bucket.isExpired(now) && buckets.remove(key, bucket)) {
                entrySlots.release();
            }
        });
    }

    private static void validate(int capacity, Duration refillPeriod) {
        if (capacity < 1 || refillPeriod == null || refillPeriod.isZero() || refillPeriod.isNegative()
                || refillPeriod.toMillis() < 1) {
            throw new IllegalArgumentException("capacity and refill period must be positive");
        }
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
        private static Decision permit() {
            return new Decision(true, 0);
        }

        private static Decision rejected(long retryAfterSeconds) {
            return new Decision(false, retryAfterSeconds);
        }
    }

    private static final class TokenBucket {
        private final int capacity;
        private final long refillPeriodMillis;
        private double tokens;
        private long lastRefillMillis;
        private long lastAccessMillis;

        private TokenBucket(int capacity, long refillPeriodMillis, long now) {
            this.capacity = capacity;
            this.refillPeriodMillis = refillPeriodMillis;
            this.tokens = capacity;
            this.lastRefillMillis = now;
            this.lastAccessMillis = now;
        }

        private synchronized Decision check(long now, boolean consume) {
            long elapsed = Math.max(0, now - lastRefillMillis);
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + (double) elapsed * capacity / refillPeriodMillis);
                lastRefillMillis = now;
            }
            lastAccessMillis = now;

            if (tokens >= 1.0) {
                if (consume) {
                    tokens -= 1.0;
                }
                return Decision.permit();
            }

            double millisUntilToken = (1.0 - tokens) * refillPeriodMillis / capacity;
            long retryAfterSeconds = (long) Math.ceil(millisUntilToken / 1000.0);
            return Decision.rejected(Math.max(1, retryAfterSeconds));
        }

        private synchronized boolean isExpired(long now) {
            return now - lastAccessMillis >= refillPeriodMillis;
        }
    }
}
