package com.minima-AI.auth.apikey;

import com.minima-AI.auth.apikey.dto.ApiKeyGenerateRequest;
import com.minima-AI.auth.apikey.dto.ApiKeyMetadataResponse;
import com.minima-AI.auth.apikey.dto.ApiKeyResponse;
import com.minima-AI.auth.common.exception.ApiKeyNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyServiceTest {

    @Mock
    ApiKeyRepository apiKeyRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    ApiKeyService apiKeyService;

    private static final UUID USER_ID = UUID.randomUUID();

    private static final String KEY_PREFIX = "mcp:key:";

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository, stringRedisTemplate);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // Helper - runs generate() with TransactionSynchronizationManager mocked,
    // captures the registered sync, and fires afterCommit() to simulate the
    // transaction actually committing. Returns the response so callers can
    // still assert on it.
    private ApiKeyResponse generateAndCommit(ApiKeyGenerateRequest request) {

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        ApiKeyResponse response;

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            response = apiKeyService.generate(USER_ID, request);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        syncCaptor.getValue().afterCommit();

        return response;
    }

    @Test
    void generate_doesNotWriteToRedis_beforeCommit() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("My Cursor Key");

        ApiKey savedKey = ApiKey.builder()
                .id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("ab12cd34").keyHash("fakehash").name("My Cursor Key")
                .createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        // fix under test: the Redis write must be deferred, not synchronous -
        // no commit simulated here on purpose
        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            apiKeyService.generate(USER_ID, request);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }

        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    void generate_persistsEntityAndCachesInRedis_onlyAfterCommit() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("My Cursor Key");

        ApiKey savedKey = ApiKey.builder()
                .id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("ab12cd34").keyHash("fakehash").name("My Cursor Key")
                .createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        ApiKeyResponse response = generateAndCommit(request);

        // rawKey must be returned exactly once and start with "sk_"
        assertThat(response.rawKey()).startsWith("sk_");
        assertThat(response.name()).isEqualTo("My Cursor Key");
        assertThat(response.id()).isEqualTo(savedKey.getId());

        verify(apiKeyRepository).save(any(ApiKey.class));

        // Redis set called with hash-based key (not raw key) - only after the
        // simulated commit fired
        ArgumentCaptor<String> redisKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(redisKeyCaptor.capture(), eq(USER_ID.toString()));

        assertThat(redisKeyCaptor.getValue()).startsWith(KEY_PREFIX);
        assertThat(redisKeyCaptor.getValue()).doesNotContain("sk_");
    }

    @Test
    void generate_rawKeyIsDifferentEachCall() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("Key");

        ApiKey key1 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("aaaa1111").keyHash("hash1").name("Key").createdAt(Instant.now()).build();

        ApiKey key2 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("bbbb2222").keyHash("hash2").name("Key").createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any())).thenReturn(key1).thenReturn(key2);

        ApiKeyResponse r1 = generateAndCommit(request);
        ApiKeyResponse r2 = generateAndCommit(request);

        // SecureRandom ensures uniqueness
        assertThat(r1.rawKey()).isNotEqualTo(r2.rawKey());
    }

    @Test
    void generate_storedEntityContainsHashNotRawKey() {

        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("Test");

        ApiKey savedKey = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("ab12cd34").keyHash("somehash").name("Test").createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any(ApiKey.class))).thenReturn(savedKey);

        generateAndCommit(request);

        ArgumentCaptor<ApiKey> entityCaptor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(entityCaptor.capture());

        ApiKey entity = entityCaptor.getValue();

        // hash must be 64-char hex (SHA-256)
        assertThat(entity.getKeyHash()).hasSize(64);
        assertThat(entity.getKeyHash()).matches("[a-f0-9]+");

        // raw key must NOT be stored
        assertThat(entity.getKeyHash()).doesNotContain("sk_");
    }

    @Test
    void generate_crashAfterCommitBeforeCallbackFires_leavesRedisUncached_failsClosed() {

        // Simulates the residual risk this fix leaves on purpose: DB row
        // exists, afterCommit() never got to run. Key is unusable via
        // McpKeyFilter until the callback would have fired - i.e. it fails
        // closed, not open. This is the acceptable-tradeoff case documented
        // on the fix - just asserting it explicitly here so it's not lost.
        ApiKeyGenerateRequest request = new ApiKeyGenerateRequest("Key");

        ApiKey savedKey = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("aaaa1111").keyHash("hash1").name("Key").createdAt(Instant.now()).build();

        when(apiKeyRepository.save(any())).thenReturn(savedKey);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            apiKeyService.generate(USER_ID, request);
            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(any()));
        }

        // afterCommit() deliberately never invoked here
        verify(apiKeyRepository).save(any(ApiKey.class));
        verify(valueOps, never()).set(anyString(), anyString());
    }

    @Test
    void list_returnsMetadataForUser() {

        ApiKey k1 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("aa11bb22").keyHash("h1").name("Key 1").createdAt(Instant.now()).build();

        ApiKey k2 = ApiKey.builder().id(UUID.randomUUID()).userId(USER_ID)
                .keyPrefix("cc33dd44").keyHash("h2").name("Key 2").createdAt(Instant.now()).build();

        when(apiKeyRepository.findAllByUserId(USER_ID)).thenReturn(List.of(k1, k2));

        List<ApiKeyMetadataResponse> result = apiKeyService.list(USER_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ApiKeyMetadataResponse::name)
                .containsExactlyInAnyOrder("Key 1", "Key 2");
        // rawKey must NOT appear in metadata response (no rawKey field in record)
    }

    @Test
    void list_noKeys_returnsEmptyList() {

        when(apiKeyRepository.findAllByUserId(USER_ID)).thenReturn(List.of());

        assertThat(apiKeyService.list(USER_ID)).isEmpty();
    }

    @Test
    void revoke_keyNotOwnedByUser_throwsApiKeyNotFound() {

        UUID keyId = UUID.randomUUID();

        when(apiKeyRepository.findByIdAndUserId(keyId, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiKeyService.revoke(USER_ID, keyId))
                .isInstanceOf(ApiKeyNotFoundException.class);

        verify(stringRedisTemplate, never()).delete(anyString());
        verify(apiKeyRepository, never()).delete(any());
    }
}

