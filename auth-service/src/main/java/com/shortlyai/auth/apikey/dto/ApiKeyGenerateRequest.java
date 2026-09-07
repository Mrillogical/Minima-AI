package com.minima-AI.auth.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiKeyGenerateRequest(

        // Human label so user can identify which key is which in the list
        @NotBlank(message = "name is required")
        @Size(max = 100, message = "name must be 100 chars or fewer")
        String name
) {}

