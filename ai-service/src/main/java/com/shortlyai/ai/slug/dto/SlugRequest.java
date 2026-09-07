package com.minima-AI.ai.slug.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SlugRequest(
        @NotBlank(message = "url must not be blank")
        @Pattern(
                regexp = "^https?://[\\w.-]+\\.[a-zA-Z]{2,}(/.*)?$",
                message = "url must be a valid http(s) URL"
        )
        String url,
        @Size(max = 200, message = "context must not exceed 200 characters")
        String context  // optional: e.g. "blog post about Spring Boot 4"
) {}

