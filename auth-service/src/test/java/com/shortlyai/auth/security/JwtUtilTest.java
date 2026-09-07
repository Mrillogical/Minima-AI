package com.minima-AI.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    // Secret must be >= 256 bits (32 chars) for HS256
    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long!!";

    private static final long ACCESS_EXPIRY_MS  = 900_000L;      // 15 min

    private static final long REFRESH_EXPIRY_MS = 604_800_000L;  // 7 days

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);
    }

    @Test
    void generateAccessToken_producesNonBlankToken() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(token).isNotBlank();
    }

    @Test
    void generateAccessToken_isValidOnParse() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void generateRefreshToken_isValidOnParse() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateRefreshToken(userId, "user@example.com", "ROLE_PRO");

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void extractUserId_returnsCorrectSubject() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId.toString());
    }

    @Test
    void extractEmail_returnsCorrectEmail() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void extractRole_returnsCorrectRole() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_ADMIN");

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_ADMIN");

    }

    @Test
    void isTokenValid_withExpiredToken_returnsFalse() {

        // expiry = -1ms -> token is expired the instant it is created
        JwtUtil expiredUtil = new JwtUtil(SECRET, -1L, REFRESH_EXPIRY_MS);

        UUID userId = UUID.randomUUID();

        String token = expiredUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_withTamperedSignature_returnsFalse() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        // corrupt last 5 chars of signature
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_withBlankToken_returnsFalse() {
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void isTokenValid_withWrongSecret_returnsFalse() {

        JwtUtil otherUtil = new JwtUtil("completely-different-secret-key-at-least-256-bits!!", ACCESS_EXPIRY_MS, REFRESH_EXPIRY_MS);

        UUID userId = UUID.randomUUID();

        String token = otherUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        // jwtUtil with original secret can't verify token signed by otherUtil
        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void extractExpiry_isInFuture() {

        UUID userId = UUID.randomUUID();

        String token = jwtUtil.generateAccessToken(userId, "user@example.com", "ROLE_FREE");

        assertThat(jwtUtil.extractExpiry(token))
                .isAfter(java.time.Instant.now());
    }
}

