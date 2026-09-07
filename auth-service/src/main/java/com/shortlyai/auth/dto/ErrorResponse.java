package com.minima-AI.auth.dto;

import java.time.Instant;

// Returned on all errors â€” consistent shape across all 5 services
// message â€” human readable
// path â€” which endpoint failed
// status â€” HTTP status code
public record ErrorResponse(
        int status,
        String message,
        String path,
        Instant timestamp
) {}


