package com.minima-AI.auth.audit;

import jakarta.servlet.http.HttpServletRequest;

// Stateless utility, not a Spring bean
public final class RequestMetadataExtractor {

    private RequestMetadataExtractor() {}

    public static String extractIp(HttpServletRequest request) {

        String forwarded = request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    public static String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }
}

