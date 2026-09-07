package com.minima-AI.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Cleanup job - deletes expired tokens nightly
    void deleteByExpiresAtBefore(Instant cutOffTime);

    // Added: delete individual token on logout / rotation
    void deleteByTokenHash(String tokenHash);

    // extract token if cache misses
    boolean existsByTokenHashAndExpiresAtAfter(String hashedToken, Instant now);

    void deleteByUserId(UUID userId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}

