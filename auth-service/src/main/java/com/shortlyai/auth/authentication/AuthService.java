package com.minima-AI.auth.authentication;

import com.minima-AI.auth.dto.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

// HttpServletRequest added to all methods - needed for IP + User-Agent audit logging
public interface AuthService {

    AuthResponse login(LoginRequest loginRequest, HttpServletRequest httpRequest);

    AuthResponse register(RegisterRequest registerRequest, HttpServletRequest httpRequest);

    AuthResponse refresh(RefreshTokenRequest refreshTokenRequest, HttpServletRequest httpRequest);

    UserResponse getMe(UUID userId);

    void logout(RefreshTokenRequest refreshTokenRequest, HttpServletRequest httpRequest);
}



