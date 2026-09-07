package com.minima-AI.analytics.events;

import java.time.Instant;
import java.util.UUID;

// Must match url-service UrlDeletedEvent field-for-field
// Kafka JsonDeserializer maps JSON by field name â€” any mismatch = null
public record UrlDeletedEvent(
        Long    id,         // url DB primary key â€” for logging only
        String  slug,       // we delete click_events by this
        UUID    userId,     // who deleted â€” for logging only
        Instant deletedAt
) {}

