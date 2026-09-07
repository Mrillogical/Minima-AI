package com.minima-AI.url.common.dto;

import java.time.Instant;
import java.util.UUID;

// Returned after successful URL shortening
public record ShortenResponse(
        Long id,
        String slug,
        String shortUrl,      // full URL e.g. http://localhost:8082/abc123
        String originalUrl,
        UUID userId,
        boolean isCustom,
        long clickCount,
        Instant expiresAt,
        Instant createdAt
) {}

