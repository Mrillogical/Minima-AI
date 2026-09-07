package com.minima-AI.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

// Validate + extract only, no token generation
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;

    public JwtUtil(
            @Value("${jwt.secret}") String secret
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Returns true only if signature valid AND token not expired
    public boolean isTokenValid(String token) {

        try {

            extractAllClaims(token); // throws JwtException if invalid or expired

            return true;

        } catch (JwtException e) {

            log.warn("Invalid JWT: {}", e.getMessage());

            return false;

        } catch (IllegalArgumentException e) {

            log.warn("JWT null or blank: {}", e.getMessage());

            return false;
        }
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isAccessToken(String token) {
        return "access".equals(extractAllClaims(token).get("type", String.class));
    }

    // Parses + verifies the signature ONCE and checks token type in the same pass
    public Optional<Claims> validateAccessToken(String token) {

        try {

            Claims claims = extractAllClaims(token);

            if (!"access".equals(claims.get("type", String.class))) {
                return Optional.empty();
            }

            return Optional.of(claims);

        } catch (JwtException e) {

            log.warn("Invalid JWT: {}", e.getMessage());
            return Optional.empty();

        } catch (IllegalArgumentException e) {

            log.warn("JWT null or blank: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

