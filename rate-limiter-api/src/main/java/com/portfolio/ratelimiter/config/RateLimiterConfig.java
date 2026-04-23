package com.portfolio.ratelimiter.config;

import com.portfolio.ratelimiter.algorithm.*;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@Configuration
public class RateLimiterConfig {

    @Bean @Primary
    public RateLimiter slidingWindow(StringRedisTemplate redis,
                                     RateLimiterProperties props,
                                     MeterRegistry registry) {
        RateLimiterProperties.Rule r = globalRule(props);
        log.info("SlidingWindow — limit={} windowMs={}", r.getLimit(), r.getWindowMs());
        return new SlidingWindowRateLimiter(redis, r.getLimit(), r.getWindowMs(), "sw", registry);
    }

    @Bean
    public RateLimiter tokenBucket(StringRedisTemplate redis,
                                   RateLimiterProperties props,
                                   MeterRegistry registry) {
        log.info("TokenBucket — capacity={} refill={}/s",
                props.getBucketCapacity(), props.getBucketRefillPerSec());
        return new TokenBucketRateLimiter(
                redis, props.getBucketCapacity(), props.getBucketRefillPerSec(), "tb", registry);
    }

    @Bean
    public RateLimiter fixedWindow(StringRedisTemplate redis,
                                   RateLimiterProperties props,
                                   MeterRegistry registry) {
        RateLimiterProperties.Rule r = globalRule(props);
        return new FixedWindowRateLimiter(redis, r.getLimit(), r.getWindowMs(), "fw", registry);
    }

    private RateLimiterProperties.Rule globalRule(RateLimiterProperties p) {
        return p.getRules().getOrDefault("global", new RateLimiterProperties.Rule(100, 60_000L));
    }
}
