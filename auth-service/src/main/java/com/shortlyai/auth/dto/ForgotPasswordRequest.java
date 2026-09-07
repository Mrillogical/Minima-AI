package com.minima-AI.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(

        @NotBlank(message = "Email must not be empty")
        @Email(message = "Enter a valid email")
        String email
) {}

