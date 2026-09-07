package com.minima-AI.auth.apikey.dto;

import java.time.Instant;
import java.util.UUID;

// No rawKey field - safe to return in list responses
public record ApiKeyMetadataResponse(
        UUID id,
        String prefix,    // "ab12cd34" - user sees "sk_ab12cd34..." to identify which key
        String name,
        Instant createdAt
) {}

