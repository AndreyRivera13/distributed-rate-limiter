package com.portfolio.ratelimiter.algorithm;

import com.portfolio.ratelimiter.model.RateLimitResult;
import com.portfolio.ratelimiter.support.RedisTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    private static final long CAPACITY = 3;
    // Slow refill so a burst cannot sneak in extra tokens mid-test
    private static final double REFILL_PER_SEC = 2.0;

    private StringRedisTemplate redis;
    private String prefix;
    private TokenBucketRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.connectOrAbort();
        prefix = "tb-test:" + UUID.randomUUID();
        limiter = new TokenBucketRateLimiter(
                redis, CAPACITY, REFILL_PER_SEC, prefix, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            RedisTestSupport.deleteByPattern(redis, prefix + ":*");
        }
    }

    @Test
    void burstUpToCapacityThenDenies() {
        String key = "client-tb";
        for (int i = 1; i <= CAPACITY; i++) {
            RateLimitResult result = limiter.check(key);
            assertThat(result.isAllowed()).as("token %s should be consumed", i).isTrue();
            assertThat(result.getLimit()).isEqualTo(CAPACITY);
        }
        RateLimitResult denied = limiter.check(key);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getRemaining()).isZero();
    }

    @Test
    void refillsAfterWaiting() throws InterruptedException {
        String key = "client-refill";
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(limiter.check(key).isAllowed()).isTrue();
        }
        assertThat(limiter.check(key).isAllowed()).isFalse();
        // refill rate is 2 tokens/s — wait for at least one token
        Thread.sleep(700);
        RateLimitResult afterRefill = limiter.check(key);
        assertThat(afterRefill.isAllowed()).isTrue();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(limiter.check("alice").isAllowed()).isTrue();
        }
        assertThat(limiter.check("alice").isAllowed()).isFalse();
        assertThat(limiter.check("bob").isAllowed()).isTrue();
    }

    @Test
    void nameIsTokenBucket() {
        assertThat(limiter.name()).isEqualTo("token-bucket");
    }
}
