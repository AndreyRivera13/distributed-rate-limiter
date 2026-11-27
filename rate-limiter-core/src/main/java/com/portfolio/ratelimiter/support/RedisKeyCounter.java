package com.portfolio.ratelimiter.support;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Counts Redis keys matching a pattern using non-blocking {@code SCAN}
 * instead of {@code KEYS}, which can stall a production Redis instance.
 *
 * <p>SCAN may theoretically visit a key more than once if the keyspace
 * changes mid-iteration; the result is an operational metric, not a
 * strict transactional count.
 */
public final class RedisKeyCounter {

    private static final long SCAN_COUNT = 500;

    private RedisKeyCounter() {}

    public static long countByPattern(StringRedisTemplate redis, String pattern) {
        long total = 0;
        ScanOptions options = ScanOptions.scanOptions()
                .match(pattern)
                .count(SCAN_COUNT)
                .build();
        try (Cursor<String> cursor = redis.scan(options)) {
            while (cursor.hasNext()) {
                cursor.next();
                total++;
            }
        } catch (Exception e) {
            return 0L;
        }
        return total;
    }

    public static long countByPatterns(StringRedisTemplate redis, String... patterns) {
        long total = 0;
        for (String pattern : patterns) {
            total += countByPattern(redis, pattern);
        }
        return total;
    }
}
