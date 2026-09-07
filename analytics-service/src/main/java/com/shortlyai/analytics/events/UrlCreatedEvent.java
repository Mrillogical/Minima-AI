package com.minima-AI.analytics.events;

import java.time.Instant;
import java.util.UUID;

public record UrlCreatedEvent(
        Long    urlId,
        String  slug,
        String  originalUrl,
        String  shortUrl,
        UUID    userId,
        Instant expiresAt,
        Instant createdAt
) {}

