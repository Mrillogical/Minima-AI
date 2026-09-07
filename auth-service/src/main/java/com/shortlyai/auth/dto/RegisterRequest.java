package com.minima-AI.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Record â€” immutable DTO, all fields final, constructor auto-generated
// @Valid on controller will trigger these annotations automatically
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name too long")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        String email,

        @NotBlank(message = "Password cannot be empty")
        @Size(min = 8, message = "Password must contain at least 8 characters")
        String password
) {}


