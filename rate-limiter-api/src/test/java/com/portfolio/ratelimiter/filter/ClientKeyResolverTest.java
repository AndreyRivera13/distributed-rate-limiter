package com.portfolio.ratelimiter.filter;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyResolverTest {

    @Test
    void defaultIgnoresSpoofableHeadersAndUsesRemoteAddr() {
        ClientKeyResolver resolver = new ClientKeyResolver(false, false);
        MockHttpServletRequest req = request("10.0.0.8");
        req.addHeader("X-User-ID", "attacker");
        req.addHeader("X-API-Key", "stolen");
        req.addHeader("X-Forwarded-For", "1.2.3.4, 10.0.0.1");

        assertThat(resolver.resolve(req)).isEqualTo("ip:10.0.0.8");
    }

    @Test
    void trustClientHeadersPrefersUserIdThenApiKey() {
        ClientKeyResolver resolver = new ClientKeyResolver(false, true);

        MockHttpServletRequest withUser = request("10.0.0.8");
        withUser.addHeader("X-User-ID", "alice");
        withUser.addHeader("X-API-Key", "k");
        assertThat(resolver.resolve(withUser)).isEqualTo("user:alice");

        MockHttpServletRequest withKey = request("10.0.0.8");
        withKey.addHeader("X-API-Key", "k-123");
        assertThat(resolver.resolve(withKey)).isEqualTo("apikey:k-123");
    }

    @Test
    void trustProxyUsesLeftmostForwardedFor() {
        ClientKeyResolver resolver = new ClientKeyResolver(true, false);
        MockHttpServletRequest req = request("10.0.0.8");
        req.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        req.addHeader("X-User-ID", "ignored-without-flag");

        assertThat(resolver.resolve(req)).isEqualTo("ip:203.0.113.10");
    }

    @Test
    void blankOrMissingHeadersFallBackToRemoteAddr() {
        ClientKeyResolver resolver = new ClientKeyResolver(true, true);
        MockHttpServletRequest req = request("192.0.2.1");
        req.addHeader("X-User-ID", "  ");
        req.addHeader("X-Forwarded-For", "");
        assertThat(resolver.resolve(req)).isEqualTo("ip:192.0.2.1");
    }

    @Test
    void sanitizeStripsNewlinesAndCapsLength() {
        assertThat(ClientKeyResolver.sanitize(null)).isNull();
        assertThat(ClientKeyResolver.sanitize("  ")).isNull();
        assertThat(ClientKeyResolver.sanitize("  alice\r\n  ")).isEqualTo("alice");
        String longValue = "x".repeat(300);
        assertThat(ClientKeyResolver.sanitize(longValue)).hasSize(ClientKeyResolver.MAX_KEY_LENGTH);
    }

    @Test
    void unknownWhenRemoteAddrMissing() {
        ClientKeyResolver resolver = new ClientKeyResolver(false, false);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("");
        assertThat(resolver.resolve(req)).isEqualTo("ip:unknown");
    }

    private static MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoteAddr);
        return req;
    }
}
