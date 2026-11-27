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

class SlidingWindowRateLimiterTest {

    private static final long LIMIT = 5;
    private static final long WINDOW_MS = 60_000L;

    private StringRedisTemplate redis;
    private String prefix;
    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.connectOrAbort();
        prefix = "sw-test:" + UUID.randomUUID();
        limiter = new SlidingWindowRateLimiter(
                redis, LIMIT, WINDOW_MS, prefix, new SimpleMeterRegistry());
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            RedisTestSupport.deleteByPattern(redis, prefix + ":*");
        }
    }

    @Test
    void allowsUpToLimitAndThenDenies() {
        String key = "client-sw";
        for (int i = 1; i <= LIMIT; i++) {
            RateLimitResult result = limiter.check(key);
            assertThat(result.isAllowed()).as("request %s should be allowed", i).isTrue();
            assertThat(result.getLimit()).isEqualTo(LIMIT);
            assertThat(result.getRemaining()).isEqualTo(LIMIT - i);
        }
        RateLimitResult denied = limiter.check(key);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getRemaining()).isZero();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < LIMIT; i++) {
            assertThat(limiter.check("alice").isAllowed()).isTrue();
        }
        assertThat(limiter.check("alice").isAllowed()).isFalse();
        assertThat(limiter.check("bob").isAllowed()).isTrue();
    }

    @Test
    void windowRollsOffAndAllowsAgain() throws InterruptedException {
        long shortWindow = 500L;
        String shortPrefix = prefix + "-short";
        SlidingWindowRateLimiter shortLimiter = new SlidingWindowRateLimiter(
                redis, 2, shortWindow, shortPrefix, new SimpleMeterRegistry());
        try {
            assertThat(shortLimiter.check("k").isAllowed()).isTrue();
            assertThat(shortLimiter.check("k").isAllowed()).isTrue();
            assertThat(shortLimiter.check("k").isAllowed()).isFalse();
            // Wait long enough that the previous window weight drops to ~0
            Thread.sleep(shortWindow + 200);
            assertThat(shortLimiter.check("k").isAllowed()).isTrue();
        } finally {
            RedisTestSupport.deleteByPattern(redis, shortPrefix + ":*");
        }
    }

    @Test
    void nameIsSlidingWindow() {
        assertThat(limiter.name()).isEqualTo("sliding-window");
    }
}
