package com.portfolio.ratelimiter.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Immutable result returned by every rate-limiting algorithm.
 *
 * Consumers check {@link #isAllowed()} first, then use the remaining/reset
 * fields to populate standard HTTP response headers:
 *   X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset, Retry-After
 */
@Getter
@Builder
public class RateLimitResult {

    private final boolean allowed;
    private final long    limit;
    private final long    remaining;
    private final long    resetEpochSecond;

    /** Seconds until the client may retry. Returns 0 when allowed. */
    public long getRetryAfterSeconds() {
        if (allowed) return 0;
        long diff = resetEpochSecond - (System.currentTimeMillis() / 1000);
        return Math.max(diff, 1);
    }
}
