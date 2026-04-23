package com.portfolio.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class DemoController {

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        return ResponseEntity.ok(Map.of("message", "pong", "timestamp", System.currentTimeMillis()));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(Map.of("id", id, "name", "Demo User " + id));
    }

    @PostMapping("/data")
    public ResponseEntity<Map<String, Object>> postData(
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "received", body != null ? body.size() : 0,
                "timestamp", System.currentTimeMillis()));
    }
}
