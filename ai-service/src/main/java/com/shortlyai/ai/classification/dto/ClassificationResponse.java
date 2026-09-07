package com.minima-AI.ai.classification.dto;

import java.util.List;

// category = single bucket, tags = short descriptive labels
public record ClassificationResponse(
        String title,
        String category,
        double confidence,
        List<String> tags
) {}

