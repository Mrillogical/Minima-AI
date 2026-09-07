package com.minima-AI.ai.events.dto;

import java.time.Instant;
import java.util.UUID;

// MUST match url-service's published url.created event field-for-field.
public record UrlCreatedEvent(
        Long urlId,
        String slug,
        String originalUrl,
        String shortUrl,
        UUID userId,
        Instant expiresAt,
        Instant createdAt
) {}

