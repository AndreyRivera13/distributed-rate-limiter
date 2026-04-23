package com.portfolio.ratelimiter.config;

import com.portfolio.ratelimiter.algorithm.RateLimiter;
import com.portfolio.ratelimiter.filter.RateLimitFilter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            RateLimiter rateLimiter, MeterRegistry registry) {
        var bean = new FilterRegistrationBean<>(new RateLimitFilter(rateLimiter, registry));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }
}
