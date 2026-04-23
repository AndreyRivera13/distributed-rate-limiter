package com.portfolio.ratelimiter.algorithm;

import com.portfolio.ratelimiter.model.RateLimitResult;

/**
 * Strategy interface for rate-limiting algorithms.
 * All implementations must be thread-safe and work correctly when
 * multiple API Gateway nodes call {@link #check} concurrently for the same key.
 */
public interface RateLimiter {
    RateLimitResult check(String clientKey);
    String name();
}
