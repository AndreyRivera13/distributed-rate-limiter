package com.portfolio.ratelimiter.support;

import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Builds a {@link StringRedisTemplate} against REDIS_HOST/REDIS_PORT
 * (defaults: localhost:6379). On CI, or when REDIS_HOST is set, a
 * missing Redis fails the test. Locally, integration tests are skipped
 * if Redis is not reachable.
 */
public final class RedisTestSupport {

    private RedisTestSupport() {}

    public static String redisHost() {
        String host = System.getenv("REDIS_HOST");
        return (host == null || host.isBlank()) ? "localhost" : host;
    }

    public static int redisPort() {
        String port = System.getenv("REDIS_PORT");
        if (port == null || port.isBlank()) {
            return 6379;
        }
        return Integer.parseInt(port);
    }

    public static boolean isRequired() {
        return "true".equalsIgnoreCase(System.getenv("CI"))
                || (System.getenv("REDIS_HOST") != null && !System.getenv("REDIS_HOST").isBlank());
    }

    public static StringRedisTemplate connectOrAbort() {
        String host = redisHost();
        int port = redisPort();
        try {
            LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
            factory.afterPropertiesSet();
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            redis.opsForValue().get("__test_hc__");
            return redis;
        } catch (Exception e) {
            if (isRequired()) {
                throw new IllegalStateException(
                        "Redis is required at " + host + ":" + port
                                + " (CI or REDIS_HOST is set) but it is not reachable.",
                        e);
            }
            Assumptions.abort("Redis is not reachable at " + host + ":" + port
                    + "; skipping integration tests.");
            return null;
        }
    }

    public static void deleteByPattern(StringRedisTemplate redis, String pattern) {
        var keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
