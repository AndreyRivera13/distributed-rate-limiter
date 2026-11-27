package com.portfolio.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.ratelimiter.algorithm.RateLimiter;
import com.portfolio.ratelimiter.model.RateLimitResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Servlet filter that enforces rate limits on every /api/** request
 * except {@code /actuator} and {@code /api/metrics}.
 *
 * <p>Client identity is resolved by {@link ClientKeyResolver}. Spoofable
 * headers ({@code X-User-ID}, {@code X-API-Key}, {@code X-Forwarded-For})
 * are ignored unless the corresponding trust flags are enabled.
 *
 * @see ClientKeyResolver
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String H_LIMIT     = "X-RateLimit-Limit";
    private static final String H_REMAINING = "X-RateLimit-Remaining";
    private static final String H_RESET     = "X-RateLimit-Reset";
    private static final String H_RETRY     = "Retry-After";

    private final RateLimiter        limiter;
    private final ClientKeyResolver  clientKeyResolver;
    private final ObjectMapper       mapper  = new ObjectMapper();
    private final Counter            blocked;
    private final Timer              checkTimer;

    public RateLimitFilter(RateLimiter limiter,
                           MeterRegistry registry,
                           ClientKeyResolver clientKeyResolver) {
        this.limiter            = limiter;
        this.clientKeyResolver  = clientKeyResolver;
        this.blocked            = Counter.builder("rate_limiter.blocked_requests").register(registry);
        this.checkTimer         = Timer.builder("rate_limiter.check_duration").register(registry);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();
        if (path.startsWith("/actuator") || path.startsWith("/api/metrics")) {
            chain.doFilter(req, res);
            return;
        }

        String clientKey = clientKeyResolver.resolve(req);
        RateLimitResult result = checkTimer.record(() -> limiter.check(clientKey));

        res.setHeader(H_LIMIT,     String.valueOf(result.getLimit()));
        res.setHeader(H_REMAINING, String.valueOf(result.getRemaining()));
        res.setHeader(H_RESET,     String.valueOf(result.getResetEpochSecond()));

        if (result.isAllowed()) {
            chain.doFilter(req, res);
            return;
        }

        blocked.increment();
        log.warn("Rate limit exceeded — client={} path={}", clientKey, path);

        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setHeader(H_RETRY, String.valueOf(result.getRetryAfterSeconds()));

        mapper.writeValue(res.getWriter(), Map.of(
                "status",     429,
                "error",      "Too Many Requests",
                "message",    "Rate limit exceeded. Retry in " + result.getRetryAfterSeconds() + "s.",
                "retryAfter", result.getRetryAfterSeconds(),
                "resetAt",    result.getResetEpochSecond()
        ));
    }
}
