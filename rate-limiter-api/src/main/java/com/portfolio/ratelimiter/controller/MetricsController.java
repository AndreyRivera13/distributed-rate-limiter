package com.portfolio.ratelimiter.controller;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MetricsController {

    private final MeterRegistry       registry;
    private final StringRedisTemplate redis;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        double allowed = count("rate_limiter.requests", "result", "allowed");
        double denied  = count("rate_limiter.requests", "result", "denied");
        double blocked = count("rate_limiter.blocked_requests");
        long   keys    = redisKeys("sw:*") + redisKeys("tb:*") + redisKeys("fw:*");

        return ResponseEntity.ok(Map.of(
                "totalAllowed", (long) allowed,
                "totalDenied",  (long) denied,
                "totalBlocked", (long) blocked,
                "activeKeys",   keys,
                "allowRate",    allowed + denied > 0 ? (allowed / (allowed + denied)) * 100 : 100.0,
                "timestamp",    System.currentTimeMillis()
        ));
    }

    @GetMapping("/algorithms")
    public ResponseEntity<Map<String, Object>> algorithms() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String algo : List.of("sliding-window", "token-bucket", "fixed-window")) {
            result.put(algo, Map.of(
                    "allowed", (long) count("rate_limiter.requests", "algorithm", algo, "result", "allowed"),
                    "denied",  (long) count("rate_limiter.requests", "algorithm", algo, "result", "denied")
            ));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean redisUp = true;
        try { redis.opsForValue().get("__hc__"); } catch (Exception e) { redisUp = false; }
        return ResponseEntity.ok(Map.of(
                "status",    redisUp ? "UP" : "DEGRADED",
                "redis",     redisUp ? "UP" : "DOWN",
                "timestamp", System.currentTimeMillis()
        ));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private double count(String metric, String... tags) {
        try {
            var finder = registry.find(metric);
            if (tags.length >= 2) finder = finder.tag(tags[0], tags[1]);
            if (tags.length >= 4) finder = finder.tag(tags[2], tags[3]);
            Counter c = finder.counter();
            return c != null ? c.count() : 0.0;
        } catch (Exception e) { return 0.0; }
    }

    private long redisKeys(String pattern) {
        try {
            Set<String> keys = redis.keys(pattern);
            return keys != null ? keys.size() : 0L;
        } catch (Exception e) { return 0L; }
    }
}
