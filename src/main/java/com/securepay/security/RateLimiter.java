package com.securepay.security;

import java.time.Duration;

/** Contract shared by local and distributed rate-limit backends. */
interface RateLimiter {
    RateLimitService.Decision tryAcquire(String key, int capacity, Duration refillPeriod);
}
