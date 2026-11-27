package com.portfolio.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    private String defaultAlgorithm     = "sliding-window";
    private long   bucketCapacity       = 20;
    private double bucketRefillPerSec   = 10.0;
    private Map<String, Rule> rules     = Map.of("global", new Rule(100, 60_000L));

    /**
     * When {@code true}, honor {@code X-Forwarded-For} (leftmost hop) for client
     * identity. Enable only behind a trusted reverse proxy that overwrites or
     * strips that header. Default {@code false}.
     */
    private boolean trustProxy = false;

    /**
     * When {@code true}, honor {@code X-User-ID} and {@code X-API-Key}. Those
     * headers are spoofable unless your edge authenticates them. Default {@code false}.
     */
    private boolean trustClientHeaders = false;

    @Data
    public static class Rule {
        private long limit;
        private long windowMs;

        public Rule() {}
        public Rule(long limit, long windowMs) {
            this.limit    = limit;
            this.windowMs = windowMs;
        }
    }
}
