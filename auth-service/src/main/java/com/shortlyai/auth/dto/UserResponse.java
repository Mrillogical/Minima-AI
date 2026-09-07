package com.minima-AI.auth.dto;

import com.minima-AI.auth.user.Provider;
import com.minima-AI.auth.user.Role;

import java.time.Instant;
import java.util.UUID;

// This record controls exactly what the API reveals
public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        Provider provider,
        boolean verified,
        Instant createdAt
) {}


