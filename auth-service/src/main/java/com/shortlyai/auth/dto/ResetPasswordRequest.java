package com.minima-AI.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank(message = "Token must not be empty")
        String token,

        @NotBlank(message = "New password cannot be empty")
        @Size(min = 8, message = "New password must contain at least 8 characters")
        String newPassword
) {}

