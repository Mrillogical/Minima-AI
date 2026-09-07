package com.minima-AI.url.events;

import java.time.Instant;
import java.util.UUID;

public record UrlDeletedEvent(
        Long id,
        String slug,
        UUID userId,
        Instant deletedAt
) {}


