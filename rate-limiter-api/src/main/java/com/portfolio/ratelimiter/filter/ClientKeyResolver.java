package com.portfolio.ratelimiter.filter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the rate-limit key for a request.
 *
 * <p>{@code X-User-ID}, {@code X-API-Key} and {@code X-Forwarded-For} are
 * client-controlled unless the edge (API gateway, trusted reverse proxy)
 * authenticates or overwrites them. They are ignored unless the matching
 * flag is explicitly enabled.
 *
 * <p>Default (both flags {@code false}): only {@code request.getRemoteAddr()}
 * is used. Enable {@code rate-limiter.trust-client-headers} to honor
 * {@code X-User-ID} / {@code X-API-Key}. Enable {@code rate-limiter.trust-proxy}
 * to honor the leftmost {@code X-Forwarded-For} hop (only do this when a
 * trusted proxy sits in front and strips or overwrites that header).
 */
public class ClientKeyResolver {

    static final int MAX_KEY_LENGTH = 256;

    private final boolean trustProxy;
    private final boolean trustClientHeaders;

    public ClientKeyResolver(boolean trustProxy, boolean trustClientHeaders) {
        this.trustProxy = trustProxy;
        this.trustClientHeaders = trustClientHeaders;
    }

    public String resolve(HttpServletRequest req) {
        if (trustClientHeaders) {
            String uid = sanitize(req.getHeader("X-User-ID"));
            if (uid != null) {
                return "user:" + uid;
            }
            String apiKey = sanitize(req.getHeader("X-API-Key"));
            if (apiKey != null) {
                return "apikey:" + apiKey;
            }
        }

        if (trustProxy) {
            String forwarded = req.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String ip = sanitize(forwarded.split(",")[0].trim());
                if (ip != null) {
                    return "ip:" + ip;
                }
            }
        }

        String remote = req.getRemoteAddr();
        if (remote == null || remote.isBlank()) {
            return "ip:unknown";
        }
        return "ip:" + remote;
    }

    /** Strips CR/LF, trims, and caps length so header values cannot break Redis keys. */
    static String sanitize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.replace("\r", "").replace("\n", "").trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > MAX_KEY_LENGTH ? value.substring(0, MAX_KEY_LENGTH) : value;
    }
}
