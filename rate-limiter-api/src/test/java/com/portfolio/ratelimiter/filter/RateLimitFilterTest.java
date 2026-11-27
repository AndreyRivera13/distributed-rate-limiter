package com.portfolio.ratelimiter.filter;

import com.portfolio.ratelimiter.algorithm.RateLimiter;
import com.portfolio.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private RecordingLimiter limiter;
    private RateLimitFilter filter;
    private AtomicInteger chainCalls;

    @BeforeEach
    void setUp() {
        limiter = new RecordingLimiter(true);
        chainCalls = new AtomicInteger();
        filter = new RateLimitFilter(
                limiter,
                new SimpleMeterRegistry(),
                new ClientKeyResolver(false, false));
    }

    @Test
    void skipsLimiterForActuatorAndMetrics() throws Exception {
        assertPassedThrough("/actuator/health");
        assertPassedThrough("/api/metrics/summary");
        assertThat(limiter.keys).isEmpty();
    }

    @Test
    void allowedRequestProceedsWithRateLimitHeaders() throws Exception {
        MockHttpServletRequest req = request("/api/v1/ping", "10.1.1.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, countingChain());

        assertThat(chainCalls.get()).isEqualTo(1);
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getHeader("X-RateLimit-Limit")).isEqualTo("10");
        assertThat(res.getHeader("X-RateLimit-Remaining")).isEqualTo("9");
        assertThat(limiter.keys).containsExactly("ip:10.1.1.1");
    }

    @Test
    void deniedRequestReturns429AndDoesNotCallChain() throws Exception {
        limiter.allowed = false;
        MockHttpServletRequest req = request("/api/v1/ping", "10.1.1.1");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, countingChain());

        assertThat(chainCalls.get()).isZero();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotBlank();
        assertThat(res.getContentAsString()).contains("Too Many Requests");
    }

    @Test
    void doesNotTrustForwardedHeadersByDefault() throws Exception {
        MockHttpServletRequest req = request("/api/v1/ping", "10.1.1.1");
        req.addHeader("X-User-ID", "spoofed");
        req.addHeader("X-Forwarded-For", "8.8.8.8");

        filter.doFilter(req, new MockHttpServletResponse(), countingChain());

        assertThat(limiter.keys).containsExactly("ip:10.1.1.1");
    }

    @Test
    void usesUserIdWhenClientHeadersAreTrusted() throws Exception {
        filter = new RateLimitFilter(
                limiter,
                new SimpleMeterRegistry(),
                new ClientKeyResolver(false, true));
        MockHttpServletRequest req = request("/api/v1/ping", "10.1.1.1");
        req.addHeader("X-User-ID", "alice");

        filter.doFilter(req, new MockHttpServletResponse(), countingChain());

        assertThat(limiter.keys).containsExactly("user:alice");
    }

    private void assertPassedThrough(String path) throws ServletException, IOException {
        chainCalls.set(0);
        filter.doFilter(request(path, "10.1.1.1"), new MockHttpServletResponse(), countingChain());
        assertThat(chainCalls.get()).isEqualTo(1);
    }

    private FilterChain countingChain() {
        return (req, res) -> chainCalls.incrementAndGet();
    }

    private static MockHttpServletRequest request(String path, String remote) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", path);
        req.setRequestURI(path);
        req.setRemoteAddr(remote);
        return req;
    }

    private static final class RecordingLimiter implements RateLimiter {
        private final List<String> keys = new ArrayList<>();
        private boolean allowed;

        private RecordingLimiter(boolean allowed) {
            this.allowed = allowed;
        }

        @Override
        public RateLimitResult check(String clientKey) {
            keys.add(clientKey);
            long reset = System.currentTimeMillis() / 1000 + 30;
            return RateLimitResult.builder()
                    .allowed(allowed)
                    .limit(10)
                    .remaining(allowed ? 9 : 0)
                    .resetEpochSecond(reset)
                    .build();
        }

        @Override
        public String name() {
            return "recording";
        }
    }
}
