package com.portfolio.ratelimiter.config;

import com.portfolio.ratelimiter.algorithm.RateLimiter;
import com.portfolio.ratelimiter.filter.ClientKeyResolver;
import com.portfolio.ratelimiter.filter.RateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public ClientKeyResolver clientKeyResolver(RateLimiterProperties properties) {
        return new ClientKeyResolver(properties.isTrustProxy(), properties.isTrustClientHeaders());
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiter rateLimiter,
            MeterRegistry registry,
            ClientKeyResolver clientKeyResolver) {
        var bean = new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, registry, clientKeyResolver));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
