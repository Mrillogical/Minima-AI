package com.minima-AI.ai.events.dto;

import java.util.List;

// ai-service publishes this - url-service/analytics-service can consume
// to store category/tags against the urlId
public record UrlClassifiedEvent(
        Long urlId,
        String title,
        String category,
        double confidence,
        List<String> tags
) {}

