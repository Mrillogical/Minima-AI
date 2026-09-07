package com.minima-AI.url.events;

import java.time.Instant;
import java.util.UUID;

public record UrlCreatedEvent(
        Long urlId,        // DB primary key â€” analytics references this
        String slug,       // click tracking key â€” Redis + analytics use this
        String originalUrl, // destination URL
        String shortUrl,   // full short URL e.g. https://sly.ai/r/abc123
        UUID userId,       // who created it
        Instant expiresAt, // analytics stops tracking after this
        Instant createdAt  // when it was created
) {}

