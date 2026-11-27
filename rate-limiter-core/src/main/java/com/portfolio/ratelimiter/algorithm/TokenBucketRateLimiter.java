package com.portfolio.ratelimiter.algorithm;

import com.portfolio.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * Token Bucket — tolerates short bursts up to {@code capacity} requests
 * while enforcing a long-term average rate equal to the refill rate.
 */
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate     redis;
    private final RedisScript<List<Long>> script;
    private final long   capacity;
    private final double ratePerMs;
    private final long   ttlMs;
    private final String keyPrefix;
    private final Counter allowedCounter;
    private final Counter deniedCounter;

    /**
     * @param capacity        max burst size (tokens)
     * @param refillPerSec    tokens added per second (e.g. 10.0 = 10 req/s sustained)
     */
    public TokenBucketRateLimiter(StringRedisTemplate redis,
                                  long capacity,
                                  double refillPerSec,
                                  String keyPrefix,
                                  MeterRegistry registry) {
        this.redis      = redis;
        this.capacity   = capacity;
        this.ratePerMs  = refillPerSec / 1000.0;
        this.keyPrefix  = keyPrefix;
        this.ttlMs      = (long) (capacity / ratePerMs * 2);
        @SuppressWarnings("unchecked")
        RedisScript<List<Long>> loaded = (RedisScript<List<Long>>) (RedisScript<?>) RedisScript.of(
                new ClassPathResource("token_bucket.lua"), List.class);
        this.script = loaded;
        this.allowedCounter = counter(registry, "allowed");
        this.deniedCounter  = counter(registry, "denied");
    }

    @Override
    public RateLimitResult check(String clientKey) {
        long now = System.currentTimeMillis();
        List<Long> r = redis.execute(script,
                List.of(keyPrefix + ":" + clientKey),
                String.valueOf(now),
                String.valueOf(capacity),
                String.valueOf(ratePerMs),
                String.valueOf(ttlMs));

        boolean allowed   = r.get(0) == 1L;
        long    remaining = r.get(1);
        long    resetMs   = r.get(3);

        (allowed ? allowedCounter : deniedCounter).increment();
        log.debug("[{}] key={} allowed={} tokens={}", name(), clientKey, allowed, remaining);

        return RateLimitResult.builder()
                .allowed(allowed).limit(capacity)
                .remaining(remaining).resetEpochSecond(resetMs / 1000)
                .build();
    }

    @Override public String name() { return "token-bucket"; }

    private Counter counter(MeterRegistry r, String result) {
        return Counter.builder("rate_limiter.requests")
                .tag("algorithm", name()).tag("result", result).register(r);
    }
}
