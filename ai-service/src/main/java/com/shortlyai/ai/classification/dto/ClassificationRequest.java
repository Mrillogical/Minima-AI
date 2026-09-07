package com.minima-AI.ai.classification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ClassificationRequest(
        @NotBlank(message = "url must not be blank")
        @Pattern(
                regexp = "^https?://[\\w.-]+\\.[a-zA-Z]{2,}(/.*)?$",
                message = "url must be a valid http(s) URL"
        )
        String url
) {}

