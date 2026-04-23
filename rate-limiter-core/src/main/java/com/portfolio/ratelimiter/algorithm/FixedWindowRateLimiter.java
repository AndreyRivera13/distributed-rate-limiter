package com.portfolio.ratelimiter.algorithm;

import com.portfolio.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Fixed Window Counter — simplest implementation, included for educational
 * comparison with SlidingWindowRateLimiter.
 *
 * Known limitation: boundary spike — a client can send 2× the limit across
 * two adjacent window boundaries. See docs/system-design.md for details.
 */
@Slf4j
public class FixedWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;
    private final long   limit;
    private final long   windowMs;
    private final String keyPrefix;
    private final Counter allowedCounter;
    private final Counter deniedCounter;

    public FixedWindowRateLimiter(StringRedisTemplate redis,
                                  long limit,
                                  long windowMs,
                                  String keyPrefix,
                                  MeterRegistry registry) {
        this.redis     = redis;
        this.limit     = limit;
        this.windowMs  = windowMs;
        this.keyPrefix = keyPrefix;
        this.allowedCounter = counter(registry, "allowed");
        this.deniedCounter  = counter(registry, "denied");
    }

    @Override
    public RateLimitResult check(String clientKey) {
        long now  = System.currentTimeMillis();
        long slot = now / windowMs;
        String key = keyPrefix + ":" + clientKey + ":" + slot;

        Long count = redis.opsForValue().increment(key);
        if (count == 1) redis.expire(key, Duration.ofMillis(windowMs));

        boolean allowed       = count <= limit;
        long resetEpochSecond = (slot + 1) * windowMs / 1000;

        (allowed ? allowedCounter : deniedCounter).increment();
        log.debug("[{}] key={} allowed={} count={}/{}", name(), clientKey, allowed, count, limit);

        return RateLimitResult.builder()
                .allowed(allowed).limit(limit)
                .remaining(Math.max(0, limit - count))
                .resetEpochSecond(resetEpochSecond)
                .build();
    }

    @Override public String name() { return "fixed-window"; }

    private Counter counter(MeterRegistry r, String result) {
        return Counter.builder("rate_limiter.requests")
                .tag("algorithm", name()).tag("result", result).register(r);
    }
}
