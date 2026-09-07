package com.minima-AI.auth.token;

import com.minima-AI.auth.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    SetOperations<String, String> setOps;

    @Mock
    JwtUtil jwtUtil;

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    RefreshTokenService refreshTokenService;

    private static final String TOKEN = "sample.refresh.token";

    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    private static final String USER_SET_KEY = "refresh:byUser:" + USER_ID;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
    }

    // Same convention as ApiKeyServiceTest / PasswordResetServiceTest - mock
    // TransactionSynchronizationManager as a static, capture the registered
    // synchronization, fire afterCommit() manually to simulate a real commit.
    private TransactionSynchronization captureStoreSync(String token, String userId) {

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            refreshTokenService.store(token, userId);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        return syncCaptor.getValue();
    }

    private void storeAndCommit(String token, String userId) {
        captureStoreSync(token, userId).afterCommit();
    }

    private TransactionSynchronization captureDeleteSync(String token) {

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            refreshTokenService.delete(token);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        return syncCaptor.getValue();
    }

    private void deleteAndCommit(String token) {
        captureDeleteSync(token).afterCommit();
    }

    private TransactionSynchronization captureRevokeSync(UUID userId) {

        ArgumentCaptor<TransactionSynchronization> syncCaptor =
                ArgumentCaptor.forClass(TransactionSynchronization.class);

        try (MockedStatic<TransactionSynchronizationManager> tsm =
                     mockStatic(TransactionSynchronizationManager.class)) {

            refreshTokenService.revokeAllForUser(userId);

            tsm.verify(() -> TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));
        }

        return syncCaptor.getValue();
    }

    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException(e);
        }
    }

    // ---------- store() ----------

    @Test
    void store_alreadyExpiredToken_skipsRedis() {

        // expiry in the past â†’ TTL would be negative â†’ should not store
        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().minusSeconds(10));

        refreshTokenService.store(TOKEN, USER_ID);

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
        verifyNoInteractions(setOps);
    }

    @Test
    void store_beforeCommit_writesNeitherTokenKeyNorUserSet() {

        // same "fails closed, not open" shape as ApiKeyServiceTest -
        // nothing Redis-side should exist until the transaction actually commits
        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        captureStoreSync(TOKEN, USER_ID); // registered, deliberately not committed

        verify(valueOps, never()).set(any(), any(), any(Duration.class));
        verify(setOps, never()).add(any(), any());
    }

    @Test
    void store_afterCommit_writesTokenKeyAndAddsHashToUserSet() {

        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        storeAndCommit(TOKEN, USER_ID);

        String expectedTokenKey = "refresh:" + sha256Hex(TOKEN);

        verify(valueOps).set(eq(expectedTokenKey), eq(USER_ID), any(Duration.class));

        // this is the bug fixed this session - the user-set SADD used to be
        // completely missing, which silently broke revokeAllForUser()
        verify(setOps).add(USER_SET_KEY, sha256Hex(TOKEN));

        verify(redis).expire(eq(USER_SET_KEY), any(Duration.class));
    }

    @Test
    void store_persistsHashedTokenInPostgres_notRawToken() {

        when(jwtUtil.extractExpiry(TOKEN)).thenReturn(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        storeAndCommit(TOKEN, USER_ID);

        ArgumentCaptor<RefreshToken> entityCaptor = ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository).save(entityCaptor.capture());

        assertThat(entityCaptor.getValue().getTokenHash()).isEqualTo(sha256Hex(TOKEN));
        assertThat(entityCaptor.getValue().getTokenHash()).doesNotContain(TOKEN);
    }

    // ---------- exists() ----------

    @Test
    void exists_tokenPresentInRedis_returnsTrue() {

        String expectedKey = "refresh:" + sha256Hex(TOKEN);

        when(redis.hasKey(expectedKey)).thenReturn(true);

        assertThat(refreshTokenService.exists(TOKEN)).isTrue();

        verify(redis).hasKey(expectedKey);
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void exists_tokenAbsentFromRedis_returnsFalse() {

        when(redis.hasKey("refresh:" + TOKEN)).thenReturn(false);

        assertThat(refreshTokenService.exists(TOKEN)).isFalse();
    }

    @Test
    void exists_redisReturnsNull_returnsFalse() {

        // Boolean.TRUE.equals(null) = false
        when(redis.hasKey("refresh:" + TOKEN)).thenReturn(null);

        assertThat(refreshTokenService.exists(TOKEN)).isFalse();
    }

    // ---------- delete() ----------

    @Test
    void delete_afterCommit_removesTokenKeyAndSremsFromUserSet() {

        UUID userId = UUID.fromString(USER_ID);

        String tokenHash = sha256Hex(TOKEN);

        RefreshToken existing = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(existing));

        deleteAndCommit(TOKEN);

        verify(redis).delete("refresh:" + tokenHash);

        // this is the other half of the SADD/SREM fix - without this, a
        // deleted/rotated token's hash would sit in the user-set forever,
        // and revokeAllForUser() would try to delete an already-gone key
        // (harmless, but the set would slowly accumulate stale hashes)
        verify(setOps).remove(USER_SET_KEY, tokenHash);
    }

    @Test
    void delete_tokenNotFoundInPostgres_skipsUserSetRemoval_doesNotThrow() {

        // token already deleted/rotated elsewhere - findByTokenHash comes
        // back empty. Must not NPE trying to read a userId that doesn't exist.
        when(refreshTokenRepository.findByTokenHash(sha256Hex(TOKEN))).thenReturn(Optional.empty());

        deleteAndCommit(TOKEN);

        verify(redis).delete("refresh:" + sha256Hex(TOKEN));
        verify(setOps, never()).remove(anyString(), anyString());
    }

    @Test
    void delete_beforeCommit_removesNothing() {

        when(refreshTokenRepository.findByTokenHash(sha256Hex(TOKEN))).thenReturn(Optional.empty());

        captureDeleteSync(TOKEN); // registered, deliberately not committed

        verify(redis, never()).delete(anyString());
        verifyNoInteractions(setOps);
    }

    // ---------- revokeAllForUser() ----------

    @Test
    void revokeAllForUser_deletesPostgresRowsUnconditionally() {

        UUID userId = UUID.fromString(USER_ID);

        when(setOps.members(USER_SET_KEY)).thenReturn(Set.of());

        captureRevokeSync(userId); // registered, deliberately not committed

        verify(refreshTokenRepository).deleteByUserId(userId);
    }

    @Test
    void revokeAllForUser_afterCommit_deletesEveryTrackedTokenAndTheSetItself() {

        // core assertion for the bug fixed this session: revokeAllForUser()
        // must actually delete refresh:{hash} keys, not just the Postgres
        // rows - otherwise already-issued refresh tokens keep working via
        // the Redis fast path after a password reset.
        UUID userId = UUID.fromString(USER_ID);

        String hash1 = sha256Hex("token-1");
        String hash2 = sha256Hex("token-2");

        when(setOps.members(USER_SET_KEY)).thenReturn(Set.of(hash1, hash2));

        captureRevokeSync(userId).afterCommit();

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);

        verify(redis).delete(keysCaptor.capture());

        assertThat(keysCaptor.getValue())
                .containsExactlyInAnyOrder("refresh:" + hash1, "refresh:" + hash2);

        verify(redis).delete(USER_SET_KEY);
    }

    @Test
    void revokeAllForUser_emptyUserSet_stillDeletesSetKey_skipsBulkDelete() {

        // user had zero active sessions (or the set already expired) -
        // members() returns empty. Must not attempt a zero-element bulk
        // delete, and must still clean up the (possibly stale) set key.
        UUID userId = UUID.fromString(USER_ID);

        when(setOps.members(USER_SET_KEY)).thenReturn(Set.of());

        captureRevokeSync(userId).afterCommit();

        verify(redis, never()).delete(anyList());
        verify(redis).delete(USER_SET_KEY);
    }

    @Test
    void revokeAllForUser_nullUserSet_doesNotThrow() {

        // Redis SMEMBERS on a key that never existed can come back null
        // depending on client/version - must not NPE on that.
        UUID userId = UUID.fromString(USER_ID);

        when(setOps.members(USER_SET_KEY)).thenReturn(null);

        captureRevokeSync(userId).afterCommit();

        verify(redis, never()).delete(anyList());
        verify(redis).delete(USER_SET_KEY);
    }

    @Test
    void revokeAllForUser_beforeCommit_deletesNothingFromRedis() {

        UUID userId = UUID.fromString(USER_ID);

        captureRevokeSync(userId); // registered, deliberately not committed

        // afterCommit() deliberately never invoked
        verify(refreshTokenRepository).deleteByUserId(userId);
        verifyNoInteractions(setOps);
        verify(redis, never()).delete(anyString());
        verify(redis, never()).delete(anyList());
    }
}

