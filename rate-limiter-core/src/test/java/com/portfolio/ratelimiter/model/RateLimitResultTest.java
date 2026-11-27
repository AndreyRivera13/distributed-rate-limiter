package com.portfolio.ratelimiter.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitResultTest {

    @Test
    void retryAfterIsZeroWhenAllowed() {
        RateLimitResult result = RateLimitResult.builder()
                .allowed(true)
                .limit(10)
                .remaining(9)
                .resetEpochSecond(System.currentTimeMillis() / 1000 + 30)
                .build();
        assertThat(result.getRetryAfterSeconds()).isZero();
    }

    @Test
    void retryAfterIsAtLeastOneWhenDenied() {
        RateLimitResult alreadyPast = RateLimitResult.builder()
                .allowed(false)
                .limit(10)
                .remaining(0)
                .resetEpochSecond(0)
                .build();
        assertThat(alreadyPast.getRetryAfterSeconds()).isEqualTo(1);

        long future = System.currentTimeMillis() / 1000 + 12;
        RateLimitResult upcoming = RateLimitResult.builder()
                .allowed(false)
                .limit(10)
                .remaining(0)
                .resetEpochSecond(future)
                .build();
        assertThat(upcoming.getRetryAfterSeconds()).isBetween(11L, 12L);
    }
}
