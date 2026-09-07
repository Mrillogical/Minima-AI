package com.minima-AI.auth.token;

import com.minima-AI.auth.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redis;

    private final JwtUtil jwtUtil;

    private final RefreshTokenRepository refreshTokenRepository;

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private static final String USER_TOKENS_PREFIX = "refresh:byUser:";

    // Store token in Redis (primary, fast) AND Postgres (backup + cleanup source).
    @Transactional
    public void store(String refreshToken, String userId) {

        Instant expiry = jwtUtil.extractExpiry(refreshToken);

        long ttlSeconds = Duration.between(Instant.now(), expiry).getSeconds();

        if (ttlSeconds <= 0) {

            log.warn("Refresh token already expired, not storing");

            return;
        }

        String tokenHash = sha256Hex(refreshToken);

        RefreshToken entity = RefreshToken.builder()
                .userId(UUID.fromString(userId))
                .tokenHash(tokenHash)
                .expiresAt(expiry)
                .build();

        refreshTokenRepository.save(entity);

        String userSetKey = USER_TOKENS_PREFIX + userId;

        TransactionSynchronizationManager.registerSynchronization(

                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        redis.opsForValue().set(
                                REFRESH_TOKEN_PREFIX + tokenHash,
                                userId,
                                Duration.ofSeconds(ttlSeconds)
                        );

                        redis.opsForSet().add(userSetKey, tokenHash);

                        redis.expire(userSetKey, Duration.ofSeconds(ttlSeconds));
                    }
                }
        );

        log.debug("Stored refresh token (Redis + Postgres) for userId: {}", userId);
    }

    // Redis is primary - exists() only checks Redis (fast path)
    public boolean exists(String refreshToken) {

        String tokenHash = sha256Hex(refreshToken);

        Boolean existsInRedis = redis.hasKey(REFRESH_TOKEN_PREFIX + tokenHash);

        if (Boolean.TRUE.equals(existsInRedis)) {

            return true;
        }

        return refreshTokenRepository
                .existsByTokenHashAndExpiresAtAfter(
                        sha256Hex(refreshToken),
                        Instant.now()
                );
    }

    // Delete from both stores - Redis invalidates immediately, Postgres cleans up audit trail
    @Transactional
    public void delete(String refreshToken) {

        String tokenHash = sha256Hex(refreshToken);

        // Looked up before delete purely to get userId for the SREM below -
        // deleteByTokenHash() doesn't tell us what it deleted.
        Optional<RefreshToken> existing = refreshTokenRepository.findByTokenHash(tokenHash);

        refreshTokenRepository.deleteByTokenHash(tokenHash);

        TransactionSynchronizationManager.registerSynchronization(

                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        redis.delete(REFRESH_TOKEN_PREFIX + tokenHash);

                        existing.ifPresent(rt ->
                                redis.opsForSet().remove(
                                        USER_TOKENS_PREFIX + rt.getUserId(),
                                        tokenHash
                                )
                        );
                    }
                }
        );

        log.debug("Deleted refresh token from Redis + Postgres");
    }

    // Kills EVERY session for a user - used by password reset
    @Transactional
    public void revokeAllForUser(UUID userId) {

        String userSetKey = USER_TOKENS_PREFIX + userId;

        refreshTokenRepository.deleteByUserId(userId);

        TransactionSynchronizationManager
                .registerSynchronization(

                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                Set<String> tokenHashes = redis.opsForSet().members(userSetKey);

                                if (tokenHashes != null && !tokenHashes.isEmpty()) {

                                    List<String> keys = tokenHashes.stream()
                                            .map(hash -> REFRESH_TOKEN_PREFIX + hash)
                                            .toList();

                                    redis.delete(keys);
                                }

                                redis.delete(userSetKey);
                            }
                        }
                );

        log.info("Revoked all refresh tokens for userId: {}", userId);
    }

    // SHA-256 hex alg
    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            // SHA-256 is mandatory in every JVM - never throws
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

