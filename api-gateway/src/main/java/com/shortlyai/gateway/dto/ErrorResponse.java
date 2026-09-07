package com.minima-AI.gateway.dto;

import java.time.Instant;

// Same record shape used in auth-service + analytics-service
// All 5 services return identical error structure - API clients handle one format
public record ErrorResponse(
        int status,      // HTTP status code e.g. 401, 429, 500
        String message,  // human-readable reason
        String path,     // which endpoint failed
        Instant timestamp
) {}

