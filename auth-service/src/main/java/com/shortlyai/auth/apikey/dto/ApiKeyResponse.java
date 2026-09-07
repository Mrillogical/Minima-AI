package com.minima-AI.auth.apikey.dto;

import java.time.Instant;
import java.util.UUID;

// rawKey included ONLY on creation response - never stored, never returned again
// User must copy it immediately - same UX as GitHub PATs
public record ApiKeyResponse(
        UUID id,
        String prefix,     // "ab12cd34" - helps user identify key in list later
        String name,
        String rawKey,     // "sk_ab12cd34..." - ONLY present on creation
        Instant createdAt
) {}

