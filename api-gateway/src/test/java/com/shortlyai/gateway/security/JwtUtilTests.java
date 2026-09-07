package com.minima-AI.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTests {

    // 256-bit test secret â€” HS256 requires >= 32 chars
    private static final String SECRET = "test-secret-key-must-be-at-least-32-chars-long!";

    private JwtUtil jwtUtil;

    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    private String buildToken(UUID userId, String role, Date expiry) {

        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(Date.from(Instant.now()))
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    @Test
    void isTokenValid_wellSignedNonExpiredToken_returnsTrue() {

        String token = buildToken(UUID.randomUUID(), "ROLE_FREE", Date.from(Instant.now().plusSeconds(3600)));

        assertThat(jwtUtil.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {

        String token = buildToken(UUID.randomUUID(), "ROLE_FREE", Date.from(Instant.now().minusSeconds(10)));

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_tamperedSignature_returnsFalse() {

        String token = buildToken(UUID.randomUUID(), "ROLE_FREE", Date.from(Instant.now().plusSeconds(3600)));
        String tampered = token.substring(0, token.length() - 4) + "abcd";

        assertThat(jwtUtil.isTokenValid(tampered)).isFalse();
    }

    @Test
    void isTokenValid_signedWithDifferentKey_returnsFalse() {

        SecretKey wrongKey = Keys.hmacShaKeyFor("a-completely-different-32-byte-secret!!".getBytes());

        String token = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("role", "ROLE_FREE")
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(wrongKey)
                .compact();

        assertThat(jwtUtil.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_nullOrBlankToken_returnsFalseWithoutThrowing() {

        assertThat(jwtUtil.isTokenValid(null)).isFalse();
        assertThat(jwtUtil.isTokenValid("")).isFalse();
    }

    @Test
    void extractUserId_returnsSubjectClaim() {

        UUID userId = UUID.randomUUID();
        String token = buildToken(userId, "ROLE_FREE", Date.from(Instant.now().plusSeconds(3600)));

        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId.toString());
    }

    @Test
    void extractRole_returnsRoleClaim() {

        String token = buildToken(UUID.randomUUID(), "ROLE_ADMIN", Date.from(Instant.now().plusSeconds(3600)));

        assertThat(jwtUtil.extractRole(token)).isEqualTo("ROLE_ADMIN");
    }
}

