package com.minima-AI.analytics.clicks;

import com.minima-AI.analytics.events.UrlClickedEvent;
import com.minima-AI.analytics.events.UrlDeletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClickServiceImplTest {

    @Mock
    private ClickEventRepository clickEventRepository;

    @Mock
    private BloomFilterService bloomFilterService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ClickHourlyRepository clickHourlyRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private UrlOwnerRepository urlOwnerRepository;

    private ClickServiceImpl clickService;

    private static final String OWNER_KEY_PREFIX = "url:owner:";

    private final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    @BeforeEach
    void setUp() {

        clickService = new ClickServiceImpl(
                clickEventRepository, bloomFilterService, redisTemplate, clickHourlyRepository, urlOwnerRepository);
    }

    @Test
    void processClick_savesEventAndIncrementsCounter_whenNotDuplicate() {

        Instant now = Instant.parse("2026-06-19T10:15:30Z");

        UrlClickedEvent event = new UrlClickedEvent(
                42L, "abc123", "Mozilla/5.0", "hash123",
                "https://google.com", "US", "NYC", now, UUID.randomUUID());

        String expectedFingerprint = "42:hash123:" + (now.getEpochSecond() / 60);

        when(bloomFilterService.isDuplicate(expectedFingerprint)).thenReturn(false);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TransactionSynchronizationManager.initSynchronization();

        try {
            clickService.processClick(event);

            verify(clickEventRepository).save(any(ClickEvent.class));

            // These operations are deferred until transaction commit
            verify(bloomFilterService, never()).markSeen(anyString());
            verify(valueOperations, never()).increment(anyString());

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            verify(bloomFilterService).markSeen(expectedFingerprint);

            verify(valueOperations).increment("clicks:realtime:42");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void processClick_skipsSaving_whenDuplicate() {

        Instant now = Instant.now();

        UrlClickedEvent event = new UrlClickedEvent(
                7L, "xyz", "ua", "iphash", "ref", "IN", "Mysuru", now, UUID.randomUUID());

        when(bloomFilterService.isDuplicate(anyString())).thenReturn(true);

        clickService.processClick(event);

        verify(clickEventRepository, never()).save(any());

        verify(bloomFilterService, never()).markSeen(anyString());

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void processClick_nullClickedAt_fallsBackToNow_doesNotThrow() {

        // event.clickedAt() is null - fingerprint building must not NPE
        UrlClickedEvent event = new UrlClickedEvent(
                9L,
                "nullts",
                "ua",
                "iphash",
                "ref",
                "IN",
                "Mysuru",
                null,
                UUID.randomUUID()
        );

        when(bloomFilterService.isDuplicate(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        TransactionSynchronizationManager.initSynchronization();

        try {
            clickService.processClick(event);

            // Verify synchronization was registered
            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // afterCommit() has not executed yet
            verify(bloomFilterService, never()).markSeen(anyString());
            verify(valueOperations, never()).increment(anyString());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            verify(clickEventRepository).save(any(ClickEvent.class));
            verify(bloomFilterService).markSeen(anyString());
            verify(valueOperations).increment("clicks:realtime:9");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getTotalClicks_ownerCacheHit_returnsFromRedisCounter_neverTouchesDb() {

        UUID userId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // ownership cache hit â€” matches caller
        when(valueOperations.get(OWNER_KEY_PREFIX + "99")).thenReturn(USER_ID);

        // counter cache hit
        when(valueOperations.get("clicks:realtime:99")).thenReturn("17");

        long total = clickService.getTotalClicks(99L, userId);

        assertThat(total).isEqualTo(17L);

        verify(clickEventRepository, never()).existsByUrlIdAndUserId(any(), any());
        verify(clickEventRepository, never()).countByUrlId(anyLong());
    }

    @Test
    void getTotalClicks_ownerCacheHit_counterCacheMiss_fallsBackToPostgresCount() {

        UUID userId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        when(valueOperations.get(OWNER_KEY_PREFIX + "99")).thenReturn(USER_ID);
        when(valueOperations.get("clicks:realtime:99")).thenReturn(null);

        when(clickEventRepository.countByUrlId(99L)).thenReturn(5L);

        long total = clickService.getTotalClicks(99L, userId);

        assertThat(total).isEqualTo(5L);

        verify(clickEventRepository).countByUrlId(99L);
        verify(clickEventRepository, never()).existsByUrlIdAndUserId(any(), any());
    }

    @Test
    void getTotalClicks_ownerCacheMiss_fallsBackToOwnershipCheck_ownerFound() {

        UUID userId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(OWNER_KEY_PREFIX + "99"))
                .thenReturn(null);

        when(urlOwnerRepository.existsByUrlIdAndUserId(99L, userId))
                .thenReturn(true);

        when(valueOperations.get("clicks:realtime:99"))
                .thenReturn("17");

        long total = clickService.getTotalClicks(99L, userId);

        assertThat(total).isEqualTo(17L);

        verify(urlOwnerRepository)
                .existsByUrlIdAndUserId(99L, userId);
    }

    @Test
    void deleteClickData_removesPostgresRowsByUrlId_andRedisCounterAndOwnerKey() {

        UrlDeletedEvent event = new UrlDeletedEvent(
                55L,
                "slug55",
                UUID.randomUUID(),
                Instant.now()
        );

        TransactionSynchronizationManager.initSynchronization();

        try {
            clickService.deleteClickData(event);

            verify(clickEventRepository).deleteByUrlId(55L);
            verify(clickHourlyRepository).deleteByUrlId(55L);
            verify(urlOwnerRepository).deleteByUrlId(55L);

            // Redis deletion must not happen before transaction commit
            verify(redisTemplate, never()).delete("clicks:realtime:55");
            verify(redisTemplate, never()).delete(OWNER_KEY_PREFIX + "55");

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            verify(redisTemplate).delete("clicks:realtime:55");
            verify(redisTemplate).delete(OWNER_KEY_PREFIX + "55");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void getHourlyBreakdown_mapsRepositoryRowsToResponses_inOrder() {

        UUID userId = UUID.fromString(USER_ID);

        Instant hour1 = Instant.parse("2026-06-19T08:00:00Z");
        Instant hour2 = Instant.parse("2026-06-19T09:00:00Z");

        List<ClickHourly> rows = List.of(
                new ClickHourly(10L, hour1, 3L),
                new ClickHourly(10L, hour2, 8L)
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY_PREFIX + "10")).thenReturn(USER_ID);

        when(clickHourlyRepository.findByUrlIdAndSince(
                eq(10L),
                any(Instant.class)))
                .thenReturn(rows);

        List<HourlyBreakdownResponse> result =
                clickService.getHourlyBreakdown(10L, userId, 24);

        assertThat(result).containsExactly(
                new HourlyBreakdownResponse(hour1, 3L),
                new HourlyBreakdownResponse(hour2, 8L)
        );
    }

    @Test
    void getTopUrls_passesCorrectPageable_andReturnsRepositoryResult() {

        List<TopUrlResponse> expected = List.of(new TopUrlResponse(1L, 100L), new TopUrlResponse(2L, 50L));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

        when(clickEventRepository
                .findTopUrlsByUserId(
                        any(UUID.class),
                        pageableCaptor.capture()
                )
        )
                .thenReturn(expected);

        List<TopUrlResponse> result = clickService
                .getTopUrls(
                        UUID.randomUUID(),
                        5
                );

        assertThat(result).isEqualTo(expected);

        assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 5));
    }

    @Test
    void getTotalClicks_ownerCacheMismatch_throwsAccessDenied_withoutTouchingDb() {

        // cache hit, but caller isn't the cached owner â€” deny immediately, no DB fallback needed
        UUID callerId = UUID.fromString(USER_ID);
        UUID actualOwnerId = UUID.randomUUID();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY_PREFIX + "999")).thenReturn(actualOwnerId.toString());

        assertThatThrownBy(() -> clickService.getTotalClicks(999L, callerId))
                .isInstanceOf(AccessDeniedException.class);

        verify(clickEventRepository, never()).existsByUrlIdAndUserId(any(), any());
        verify(clickEventRepository, never()).countByUrlId(anyLong());
    }

    @Test
    void getTotalClicks_ownerCacheMiss_andNoOwnership_throwsAccessDenied() {

        Long urlId = 999L;
        UUID callerId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(OWNER_KEY_PREFIX + "999"))
                .thenReturn(null);

        when(urlOwnerRepository.existsByUrlIdAndUserId(urlId, callerId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                clickService.getTotalClicks(urlId, callerId)
        )
                .isInstanceOf(AccessDeniedException.class);

        verify(clickEventRepository, never())
                .countByUrlId(anyLong());
    }

    @Test
    void getHourlyBreakdown_urlNotOwnedByCaller_throwsAccessDenied() {

        Long urlId = 999L;
        UUID callerId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        when(valueOperations.get(OWNER_KEY_PREFIX + "999"))
                .thenReturn(null);

        when(urlOwnerRepository.existsByUrlIdAndUserId(urlId, callerId))
                .thenReturn(false);

        assertThatThrownBy(() ->
                clickService.getHourlyBreakdown(urlId, callerId, 24)
        )
                .isInstanceOf(AccessDeniedException.class);

        verify(clickHourlyRepository, never())
                .findByUrlIdAndSince(anyLong(), any(Instant.class));
    }

    @Test
    void getTotalClicks_urlOwnedByCaller_returnsStatsNormally() {

        Long urlId = 42L;
        UUID callerId = UUID.fromString(USER_ID);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(OWNER_KEY_PREFIX + "42")).thenReturn(USER_ID);
        when(valueOperations.get("clicks:realtime:" + urlId)).thenReturn(null);
        when(clickEventRepository.countByUrlId(urlId)).thenReturn(7L);

        long result = clickService.getTotalClicks(urlId, callerId);

        assertThat(result).isEqualTo(7L);
    }
}

