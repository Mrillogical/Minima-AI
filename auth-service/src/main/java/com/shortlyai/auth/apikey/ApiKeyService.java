package com.minima-AI.auth.apikey;

import com.minima-AI.auth.apikey.dto.ApiKeyGenerateRequest;
import com.minima-AI.auth.apikey.dto.ApiKeyMetadataResponse;
import com.minima-AI.auth.apikey.dto.ApiKeyResponse;
import com.minima-AI.auth.common.exception.ApiKeyNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    private final StringRedisTemplate redis;

    // Redis key prefix - namespaced to avoid collision with refresh: or url: keys
    private static final String REDIS_MCP_KEY_PREFIX = "mcp:key:";

    // SecureRandom singleton - thread-safe, expensive to construct, reuse it
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public ApiKeyResponse generate(UUID userId, ApiKeyGenerateRequest request) {

        // Generate 42 random bytes -> 56 base64url chars (no padding)
        // base64url uses a-z, A-Z 0-9 - _ -> URL-safe, no special chars to escape
        byte[] bytes = new byte[42];

        secureRandom.nextBytes(bytes);

        String randomPart = Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        // Full raw key: "sk_" prefix makes it identifiable (e.g. in leaked logs/code)
        String rawKey = "sk_" + randomPart;

        // First 8 chars of random part - shown in list UI so user can say "that's my Cursor key"
        String prefix = randomPart.substring(0, 8);

        // SHA-256 hash - one-way, stored in DB + Redis
        // Even if DB is breached, attacker can't reconstruct raw keys
        String hash = sha256Hex(rawKey);

        // Persist metadata + hash - raw key never touches DB
        ApiKey entity = ApiKey.builder()
                .userId(userId)
                .keyPrefix(prefix)
                .keyHash(hash)
                .name(request.name())
                .build();

        ApiKey saved = apiKeyRepository.save(entity);

        // Redis write deferred to afterCommit(). Previously this ran
        // inline inside the open transaction - if the commit later failed
        // (constraint violation flushed at commit, connection drop), Redis
        // would have a live hash->userId mapping for an ApiKey row that was
        // never actually persisted
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {

                redis.opsForValue().set(
                        REDIS_MCP_KEY_PREFIX + hash,
                        userId.toString()
                );

                log.debug("Cached API key hash for userId: {}, keyId: {}", userId, saved.getId());
            }
        });

        log.info("API key generated for userId: {}, keyId: {}, name: {}", userId, saved.getId(), request.name());

        // rawKey returned ONCE here - caller must copy it immediately
        return new ApiKeyResponse(saved.getId(), prefix, saved.getName(), rawKey, saved.getCreatedAt());
    }

    public List<ApiKeyMetadataResponse> list(UUID userId) {

        return apiKeyRepository.findAllByUserId(userId).stream()
                .map(k -> new ApiKeyMetadataResponse(k.getId(), k.getKeyPrefix(), k.getName(), k.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID keyId) {

        // findByIdAndUserId - ownership check in query
        // User A cannot revoke User B's key even if they guess the UUID
        ApiKey key = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ApiKeyNotFoundException("API key not found"));

        apiKeyRepository.delete(key);

        // Redis delete deferred to afterCommit()
        String hash = key.getKeyHash();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {

                redis.delete(REDIS_MCP_KEY_PREFIX + hash);

                log.debug("Evicted API key hash from cache, keyId: {}", keyId);
            }
        });

        log.info("API key revoked userId: {}, keyId: {}", userId, keyId);
    }

    // SHA-256 of rawKey -> 64-char lowercase hex string
    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            // SHA-256 is mandatory in every JVM - this never throws in practice
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}

