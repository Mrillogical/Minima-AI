package com.minima-AI.url.common.dto;

import jakarta.validation.constraints.*;

// User gives an URL to shorten
public record ShortenRequest(

        @NotBlank(message = "URL is required")
        @Pattern(
                regexp = "^https?://.*",
                message = "URL must start with http:// or https://"
        )
        String originalUrl,

        @Size(max = 20, message = "Custom slug cannot exceed 20 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9_-]*$",
                message = "Custom slug can only contain letters, numbers, hyphens, and underscores"
        )
        String customSlug,

        @Positive(message = "Expiry days must be greater than 0")
        @Max(value = 365, message = "Expiry days cannot exceed 365")
        Integer expiryDays
) {}

