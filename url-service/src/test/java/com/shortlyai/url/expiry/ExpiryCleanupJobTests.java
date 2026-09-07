package com.minima-AI.url.expiry;

import com.minima-AI.url.shortening.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpiryCleanupJobTests {

    @Mock
    UrlRepository urlRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    ExpiryCleanupJob expiryCleanupJob;

    @BeforeEach
    void setUp() {
        expiryCleanupJob = new ExpiryCleanupJob(urlRepository, stringRedisTemplate);
    }

    @Test
    void cleanupExpiredUrls_noExpiredUrls_skipsDeactivationAndEviction() {

        when(urlRepository.findExpiredSlugs(any(Instant.class))).thenReturn(List.of());

        expiryCleanupJob.cleanupExpiredUrls();

        verify(urlRepository, never()).deactivateExpiredUrls(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void cleanupExpiredUrls_hasExpiredUrls_deactivatesAndEvictsEachFromCache() {

        List<String> expiredSlugs = List.of("slug1", "slug2", "slug3");

        when(urlRepository.findExpiredSlugs(any(Instant.class)))
                .thenReturn(expiredSlugs);

        when(urlRepository.deactivateExpiredUrls(any(Instant.class)))
                .thenReturn(3);

        TransactionSynchronizationManager.initSynchronization();

        try {
            expiryCleanupJob.cleanupExpiredUrls();

            verify(urlRepository).deactivateExpiredUrls(any(Instant.class));

            // Redis eviction must not happen before transaction commit
            verify(stringRedisTemplate, never()).delete(
                    List.of(
                            "url:slug1",
                            "url:slug2",
                            "url:slug3"
                    )
            );

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            verify(stringRedisTemplate).delete(
                    List.of(
                            "url:slug1",
                            "url:slug2",
                            "url:slug3"
                    )
            );

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void cleanupExpiredUrls_evictsExactCountMatchingSlugList() {

        List<String> expiredSlugs = List.of("only-one");

        when(urlRepository.findExpiredSlugs(any(Instant.class)))
                .thenReturn(expiredSlugs);

        when(urlRepository.deactivateExpiredUrls(any(Instant.class)))
                .thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();

        try {
            expiryCleanupJob.cleanupExpiredUrls();

            verify(urlRepository).deactivateExpiredUrls(any(Instant.class));

            // Redis eviction must not happen before transaction commit
            verify(stringRedisTemplate, never()).delete(
                    List.of("url:only-one")
            );

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            verify(stringRedisTemplate).delete(
                    List.of("url:only-one")
            );

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}

