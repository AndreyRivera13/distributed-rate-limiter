package com.portfolio.ratelimiter.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedisKeyCounterTest {

    private StringRedisTemplate redis;
    private String prefix;

    @BeforeEach
    void setUp() {
        redis = RedisTestSupport.connectOrAbort();
        prefix = "testscan:" + UUID.randomUUID() + ":";
    }

    @AfterEach
    void tearDown() {
        if (redis != null) {
            RedisTestSupport.deleteByPattern(redis, prefix + "*");
        }
    }

    @Test
    void scanCountsOnlyMatchingKeys() {
        redis.opsForValue().set(prefix + "sw:user-a", "1");
        redis.opsForValue().set(prefix + "sw:user-b", "1");
        redis.opsForValue().set(prefix + "tb:user-c", "1");
        redis.opsForValue().set(prefix + "other", "1");

        assertThat(RedisKeyCounter.countByPattern(redis, prefix + "sw:*")).isEqualTo(2);
        assertThat(RedisKeyCounter.countByPattern(redis, prefix + "tb:*")).isEqualTo(1);
        assertThat(RedisKeyCounter.countByPatterns(redis, prefix + "sw:*", prefix + "tb:*")).isEqualTo(3);
        assertThat(RedisKeyCounter.countByPattern(redis, prefix + "missing:*")).isZero();
    }
}
