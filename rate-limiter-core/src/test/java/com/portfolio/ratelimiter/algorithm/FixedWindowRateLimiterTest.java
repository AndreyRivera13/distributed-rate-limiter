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

class FixedWindowRateLimiterTest {

    private static final long LIMIT = 3;
    private static final long WINDOW_MS = 60_000L;

    private StringRedisTemplate redis;
    private String prefix;
    private FixedWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.connectOrAbort();
        prefix = "fw-test:" + UUID.randomUUID();
        limiter = new FixedWindowRateLimiter(
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
        String key = "client-a";
        for (int i = 1; i <= LIMIT; i++) {
            RateLimitResult result = limiter.check(key);
            assertThat(result.isAllowed()).as("request %s should be allowed", i).isTrue();
            assertThat(result.getLimit()).isEqualTo(LIMIT);
            assertThat(result.getRemaining()).isEqualTo(LIMIT - i);
        }
        RateLimitResult denied = limiter.check(key);
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getRemaining()).isZero();
        assertThat(denied.getRetryAfterSeconds()).isGreaterThanOrEqualTo(1);
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
    void windowExpiresAndAllowsAgain() throws InterruptedException {
        long shortWindow = 400L;
        String shortPrefix = prefix + "-short";
        FixedWindowRateLimiter shortLimiter = new FixedWindowRateLimiter(
                redis, 1, shortWindow, shortPrefix, new SimpleMeterRegistry());
        try {
            assertThat(shortLimiter.check("k").isAllowed()).isTrue();
            assertThat(shortLimiter.check("k").isAllowed()).isFalse();
            Thread.sleep(shortWindow + 150);
            assertThat(shortLimiter.check("k").isAllowed()).isTrue();
        } finally {
            RedisTestSupport.deleteByPattern(redis, shortPrefix + ":*");
        }
    }

    @Test
    void nameIsFixedWindow() {
        assertThat(limiter.name()).isEqualTo("fixed-window");
    }
}
