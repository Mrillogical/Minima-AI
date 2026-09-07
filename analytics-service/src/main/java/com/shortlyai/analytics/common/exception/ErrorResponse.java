package com.minima-AI.analytics.common.exception;

import java.time.Instant;

// Overloaded compact constructor sets timestamp automatically
public record ErrorResponse(
        int    status,    // HTTP status code e.g. 404
        String message,   // human-readable error
        String path,      // HTTP request path
        Instant timestamp // when error occurred
) {
    // caller only provides status + message
    public ErrorResponse(int status, String message, String path) {
        this(status, message, path,Instant.now());
    }
}

