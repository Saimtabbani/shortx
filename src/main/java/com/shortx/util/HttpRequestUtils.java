package com.shortx.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracted from AuthServiceImpl (where it originated, for refresh-token
 * device metadata) because the click-analytics recorder in this milestone
 * needs the identical logic to resolve the real client IP for geo lookup.
 */
public final class HttpRequestUtils {

    private HttpRequestUtils() {
        // Non-instantiable utility class.
    }

    /**
     * Resolves the real client IP, accounting for a reverse proxy/load
     * balancer sitting in front of this service (any real deployment) —
     * without this, every request would appear to originate from the
     * proxy's own address rather than the actual client.
     */
    public static String extractClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Left-most entry is the original client; subsequent entries
            // are intermediate proxies the request passed through.
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
