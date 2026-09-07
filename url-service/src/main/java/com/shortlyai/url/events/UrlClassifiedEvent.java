package com.minima-AI.url.events;

import java.util.List;

public record UrlClassifiedEvent(
        Long urlId,
        String title,
        String category,
        double confidence,
        List<String> tags   // not persisted yet - no column for it
) {}

