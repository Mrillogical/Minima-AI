package com.minima-AI.analytics.events;

import java.time.Instant;
import java.util.UUID;

// Record â€” immutable Kafka message. Must match exactly what url-service publishes.
// Kafka JsonDeserializer maps JSON fields â†’ record components by name.
public record UrlClickedEvent(
        Long   urlId,       // which URL was clicked
        String slug,        // the short code e.g. "abc123"
        String userAgent,   // browser/device string
        String ipHash,      // SHA-256 of IP â€” GDPR-compliant, not raw IP
        String referer,     // where the click came from
        String country,     // geo-resolved country
        String city,        // geo-resolved city
        Instant clickedAt,  // when the click happened (set by url-service)
        UUID userId         // The user's identification
) {}

