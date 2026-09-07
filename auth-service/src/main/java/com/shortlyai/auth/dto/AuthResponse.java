package com.minima-AI.auth.dto;

// Returned after successful login or register
// accessToken â€” short lived (15 min), sent in Authorization header
// refreshToken â€” long lived (7 days), used to get new accessToken
public record AuthResponse(
        String accessToken,
        String refreshToken,
        UserResponse user
) {}


