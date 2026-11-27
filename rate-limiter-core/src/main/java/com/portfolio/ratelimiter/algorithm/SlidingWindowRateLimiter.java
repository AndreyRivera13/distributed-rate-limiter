package com.portfolio.ratelimiter.algorithm;

import com.portfolio.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Sliding Window Counter — O(1) memory, race-condition-free via Lua.
 *
 * Interpolates the previous window's count weighted by the overlap fraction,
 * preventing the boundary-spike problem of fixed windows while using only
 * two Redis keys per client.
 */
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {

    private final StringRedisTemplate    redis;
    private final RedisScript<List<Long>> script;
    private final long   limit;
    private final long   windowMs;
    private final String keyPrefix;
    private final Counter allowedCounter;
    private final Counter deniedCounter;
    private final Timer   checkTimer;

    public SlidingWindowRateLimiter(StringRedisTemplate redis,
                                    long limit,
                                    long windowMs,
                                    String keyPrefix,
                                    MeterRegistry registry) {
        this.redis     = redis;
        this.limit     = limit;
        this.windowMs  = windowMs;
        this.keyPrefix = keyPrefix;
        @SuppressWarnings("unchecked")
        RedisScript<List<Long>> loaded = (RedisScript<List<Long>>) (RedisScript<?>) RedisScript.of(
                new ClassPathResource("sliding_window.lua"), List.class);
        this.script = loaded;

        this.allowedCounter = counter(registry, "allowed");
        this.deniedCounter  = counter(registry, "denied");
        this.checkTimer     = Timer.builder("rate_limiter.check_duration")
                .tag("algorithm", name())
                .register(registry);
    }

    @Override
    public RateLimitResult check(String clientKey) {
        return checkTimer.record(() -> {
            long now      = System.currentTimeMillis();
            long slot     = now / windowMs;
            String currKey = keyPrefix + ":" + clientKey + ":" + slot;
            String prevKey = keyPrefix + ":" + clientKey + ":" + (slot - 1);

            List<Long> r = redis.execute(script,
                    List.of(currKey, prevKey),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit));

            boolean allowed = r.get(0) == 1L;
            long estimated  = r.get(1);
            long resetMs    = r.get(3);

            (allowed ? allowedCounter : deniedCounter).increment();
            log.debug("[{}] key={} allowed={} count={}/{}", name(), clientKey, allowed, estimated, limit);

            return RateLimitResult.builder()
                    .allowed(allowed)
                    .limit(limit)
                    .remaining(Math.max(0, limit - estimated))
                    .resetEpochSecond(resetMs / 1000)
                    .build();
        });
    }

    @Override public String name() { return "sliding-window"; }

    private Counter counter(MeterRegistry r, String result) {
        return Counter.builder("rate_limiter.requests")
                .tag("algorithm", name()).tag("result", result).register(r);
    }
}
