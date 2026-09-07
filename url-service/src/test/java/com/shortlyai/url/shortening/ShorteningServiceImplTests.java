package com.minima-AI.url.shortening;

import com.minima-AI.url.common.dto.ShortenRequest;
import com.minima-AI.url.common.dto.ShortenResponse;
import com.minima-AI.url.common.exception.DuplicateSlugException;
import com.minima-AI.url.common.exception.UrlNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShorteningServiceImplTests {

    @Mock
    UrlRepository urlRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    UrlEventPublisher urlEventPublisher;

    @Mock
    HttpServletRequest httpServletRequest;

    ShorteningServiceImpl service;

    private static final String BASE_DOMAIN = "http://localhost:8082";
    private static final long DEFAULT_EXPIRY_DAYS = 30;
    private static final long CACHE_TTL_SECONDS = 3600;
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String CACHE_PREFIX = "url:";

    @BeforeEach
    void setUp() {

        service = new ShorteningServiceImpl(
                urlRepository, stringRedisTemplate, urlEventPublisher,
                BASE_DOMAIN, DEFAULT_EXPIRY_DAYS, CACHE_TTL_SECONDS,
                Runnable::run);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("test-agent");
        when(httpServletRequest.getHeader("Referer")).thenReturn("https://ref.example.com");
    }

    private Url savedUrlFixture(Long id, String slug, boolean custom) {
        return Url.builder()
                .id(id)
                .slug(slug)
                .originalUrl("https://example.com/page")
                .userId(USER_ID)
                .isCustom(custom)
                .expiresAt(Instant.now().plus(DEFAULT_EXPIRY_DAYS, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .clickCount(0)
                .build();
    }

    // ---------- shorten() ----------

    @Test
    void shorten_customSlugAlreadyTaken_throwsDuplicateSlugException_noSideEffects() {

        ShortenRequest request = new ShortenRequest("https://example.com/page", "taken-slug", null);

        when(urlRepository.existsBySlug("taken-slug")).thenReturn(true);

        assertThatThrownBy(() -> service.shorten(request, USER_ID))
                .isInstanceOf(DuplicateSlugException.class);

        verify(urlRepository, never()).save(any());
        verify(urlEventPublisher, never()).publishCreated(any());
        verify(valueOps, never()).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    void shorten_noCustomSlug_exceedsMaxAttempts_throwsIllegalStateException() {

        ShortenRequest request = new ShortenRequest("https://example.com/page", null, null);

        when(urlRepository.existsBySlug(anyString())).thenReturn(true); // always collides
        when(urlRepository.save(any(Url.class))).thenReturn(savedUrlFixture(3L, "tmpslug", false));

        assertThatThrownBy(() -> service.shorten(request, USER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to generate unique slug");

        verify(urlEventPublisher, never()).publishCreated(any());
    }

    // ---------- resolve() ----------

    @Test
    void resolve_cacheHit_validAndNotExpired_returnsUrlAndDispatchesClick() {

        long expiresAtMs = Instant.now().plus(1, ChronoUnit.DAYS).toEpochMilli();
        String cached = "10\u0000" + USER_ID + "\u0000" + expiresAtMs + "\u0000https://example.com/cached";

        when(valueOps.get(CACHE_PREFIX + "slug1")).thenReturn(cached);

        String result = service.resolve("slug1", httpServletRequest);

        assertThat(result).isEqualTo("https://example.com/cached");

        // cache hit never touches Postgres
        verify(urlRepository, never()).findBySlugAndIsActiveTrueAndExpiresAtAfter(anyString(), any());

        verify(urlEventPublisher).publishClick(eq(10L), eq("slug1"), eq(USER_ID), anyString(), eq("test-agent"), eq("https://ref.example.com"));
        verify(valueOps).increment("clicks:pending:10");
    }

    @Test
    void resolve_cacheHit_expired_throwsAndEvictsCache() {

        long expiresAtMs = Instant.now().minus(1, ChronoUnit.DAYS).toEpochMilli();
        String cached = "10\u0000" + USER_ID + "\u0000" + expiresAtMs + "\u0000https://example.com/gone";

        when(valueOps.get(CACHE_PREFIX + "expired")).thenReturn(cached);

        assertThatThrownBy(() -> service.resolve("expired", httpServletRequest))
                .isInstanceOf(UrlNotFoundException.class);

        verify(stringRedisTemplate).delete(CACHE_PREFIX + "expired");
        verify(urlEventPublisher, never()).publishClick(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolve_cacheHit_staleThreePartFormat_evictsAndFallsBackToDb() {

        // old pre-migration 3-part cache format (no expiresAtMs segment)
        when(valueOps.get(CACHE_PREFIX + "stale")).thenReturn("10\u0000" + USER_ID + "\u0000https://example.com/old");

        Url dbUrl = savedUrlFixture(10L, "stale", false);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("stale"), any()))
                .thenReturn(Optional.of(dbUrl));

        String result = service.resolve("stale", httpServletRequest);

        assertThat(result).isEqualTo(dbUrl.getOriginalUrl());

        verify(stringRedisTemplate).delete(CACHE_PREFIX + "stale");
        verify(valueOps).set(eq(CACHE_PREFIX + "stale"), anyString(), eq(java.time.Duration.ofSeconds(CACHE_TTL_SECONDS)));
        verify(urlEventPublisher).publishClick(eq(10L), eq("stale"), eq(USER_ID), anyString(), anyString(), anyString());
    }

    @Test
    void resolve_cacheMiss_notInDb_throwsUrlNotFoundException() {

        when(valueOps.get(CACHE_PREFIX + "ghost")).thenReturn(null);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("ghost"), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("ghost", httpServletRequest))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlEventPublisher, never()).publishClick(any(), any(), any(), any(), any(), any());
    }

    @Test
    void resolve_cacheMiss_foundInDb_cachesAndDispatchesClick() {

        when(valueOps.get(CACHE_PREFIX + "fresh")).thenReturn(null);
        Url dbUrl = savedUrlFixture(11L, "fresh", false);
        when(urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(eq("fresh"), any()))
                .thenReturn(Optional.of(dbUrl));

        String result = service.resolve("fresh", httpServletRequest);

        assertThat(result).isEqualTo(dbUrl.getOriginalUrl());
        verify(valueOps).set(eq(CACHE_PREFIX + "fresh"), anyString(), eq(java.time.Duration.ofSeconds(CACHE_TTL_SECONDS)));
        verify(urlEventPublisher).publishClick(eq(11L), eq("fresh"), eq(USER_ID), anyString(), anyString(), anyString());
    }

    // ---------- delete(id, userId) ----------

    @Test
    void delete_ownedAndActive_softDeletesEvictsCacheAndPublishesDeleted() {

        Url url = savedUrlFixture(20L, "todelete", false);

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(20L, USER_ID))
                .thenReturn(Optional.of(url));

        when(urlRepository.softDeleteByIdAndUserId(
                eq(20L),
                eq(USER_ID),
                any()
        )).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();

        try {
            service.delete(20L, USER_ID);

            // DB soft delete happens inside the transaction
            verify(urlRepository).softDeleteByIdAndUserId(
                    eq(20L),
                    eq(USER_ID),
                    any()
            );

            // Redis eviction is deferred until transaction commit
            verify(stringRedisTemplate, never())
                    .delete(CACHE_PREFIX + "todelete");

            // Event is still published during the service method
            verify(urlEventPublisher).publishDeleted(url);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            // Redis eviction happens only after commit
            verify(stringRedisTemplate)
                    .delete(CACHE_PREFIX + "todelete");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void delete_notFound_throwsAndNoSideEffects() {

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(99L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, USER_ID))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlRepository, never()).softDeleteByIdAndUserId(any(), any(), any());
        verify(urlEventPublisher, never()).publishDeleted(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    @Test
    void delete_foundButRaceConditionZeroRowsAffected_throwsWithoutPublishing() {

        // fetch succeeds, but another request soft-deleted it in between -
        // softDeleteByIdAndUserId affects 0 rows. Must still throw and must
        // NOT evict cache / publish for a delete that didn't actually happen.
        Url url = savedUrlFixture(21L, "raced", false);

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(21L, USER_ID)).thenReturn(Optional.of(url));
        when(urlRepository.softDeleteByIdAndUserId(eq(21L), eq(USER_ID), any())).thenReturn(0);

        assertThatThrownBy(() -> service.delete(21L, USER_ID))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlEventPublisher, never()).publishDeleted(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }

    // ---------- deleteUrl(slug, userId) ----------

    @Test
    void deleteUrl_ownedAndActive_softDeletesEvictsCacheAndPublishesDeleted() {

        Url url = savedUrlFixture(22L, "byslug", false);

        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("byslug", USER_ID))
                .thenReturn(Optional.of(url));

        when(urlRepository.softDeleteBySlugAndUserId(
                eq("byslug"),
                eq(USER_ID),
                any()
        )).thenReturn(1);

        TransactionSynchronizationManager.initSynchronization();

        try {
            service.deleteUrl("byslug", USER_ID);

            // DB soft delete happens inside the transaction
            verify(urlRepository).softDeleteBySlugAndUserId(
                    eq("byslug"),
                    eq(USER_ID),
                    any()
            );

            // Redis eviction is deferred until transaction commit
            verify(stringRedisTemplate, never())
                    .delete(CACHE_PREFIX + "byslug");

            // Event is still published during the service method
            verify(urlEventPublisher).publishDeleted(url);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();

            assertEquals(1, synchronizations.size());

            // Simulate successful transaction commit
            synchronizations.get(0).afterCommit();

            // Redis eviction happens only after commit
            verify(stringRedisTemplate)
                    .delete(CACHE_PREFIX + "byslug");

        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deleteUrl_slugNotFound_throwsUrlNotFoundException() {

        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("missing", USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUrl("missing", USER_ID))
                .isInstanceOf(UrlNotFoundException.class);

        verify(urlEventPublisher, never()).publishDeleted(any());
    }

    // ---------- reads ----------

    @Test
    void getUrl_ownedAndActive_returnsMappedResponse() {

        Url url = savedUrlFixture(30L, "readable", false);
        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(30L, USER_ID)).thenReturn(Optional.of(url));

        ShortenResponse response = service.getUrl(30L, USER_ID);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.slug()).isEqualTo("readable");
    }

    @Test
    void getUrl_notFound_throwsUrlNotFoundException() {

        when(urlRepository.findByIdAndUserIdAndIsActiveTrue(31L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrl(31L, USER_ID))
                .isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void getUserUrls_mapsPageOfEntitiesToResponses() {

        Url url = savedUrlFixture(32L, "paged", false);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Url> page = new PageImpl<>(List.of(url), pageable, 1);

        when(urlRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(USER_ID, pageable)).thenReturn(page);

        Page<ShortenResponse> result = service.getUserUrls(USER_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).slug()).isEqualTo("paged");
    }

    @Test
    void getUrlBySlug_ownedAndActive_returnsMappedResponse() {

        Url url = savedUrlFixture(33L, "bysluglookup", false);
        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("bysluglookup", USER_ID)).thenReturn(Optional.of(url));

        ShortenResponse response = service.getUrlBySlug("bysluglookup", USER_ID);

        assertThat(response.slug()).isEqualTo("bysluglookup");
    }

    @Test
    void getUrlBySlug_notFound_throwsUrlNotFoundException() {

        when(urlRepository.findBySlugAndUserIdAndIsActiveTrue("nope", USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUrlBySlug("nope", USER_ID))
                .isInstanceOf(UrlNotFoundException.class);
    }
}

