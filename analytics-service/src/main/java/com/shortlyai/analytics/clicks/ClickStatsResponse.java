package com.minima-AI.analytics.clicks;

// Record DTO â€” immutable, no boilerplate, serializes perfectly to JSON
public record ClickStatsResponse(
        Long   urlId,
        long   totalClicks,
        String message
) {}

