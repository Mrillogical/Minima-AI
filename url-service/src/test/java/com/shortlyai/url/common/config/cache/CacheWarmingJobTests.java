package com.minima-AI.url.common.config.cache;

import com.minima-AI.url.shortening.Url;
import com.minima-AI.url.shortening.UrlRepository;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheWarmingJobTests {

    @Mock
    UrlRepository urlRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    CacheWarmingJob cacheWarmingJob;

    @BeforeEach
    void setUp() {

        cacheWarmingJob = new CacheWarmingJob(urlRepository, stringRedisTemplate, 3600L);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForValue().setIfAbsent(eq("lock:cache-warming"), anyString(), any(Duration.class)))
                .thenReturn(true);
    }

    private Url activeUrl(long id, String slug) {

        return Url.builder()
                .id(id)
                .slug(slug)
                .originalUrl("https://example.com/" + slug)
                .userId(UUID.randomUUID())
                .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .clickCount(100L)
                .build();
    }

    private Url expiredUrl(long id, String slug) {

        return Url.builder()
                .id(id)
                .slug(slug)
                .originalUrl("https://example.com/" + slug)
                .userId(UUID.randomUUID())
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .clickCount(50L)
                .build();
    }

    @Test
    void cacheWarmup_activeUrls_actuallyWritesToRedis() {

        Page<Url> page = new PageImpl<>(List.of(activeUrl(1L, "hot1"), activeUrl(2L, "hot2")));

        when(urlRepository.findByIsActiveTrueOrderByClickCountDesc(any(PageRequest.class))).thenReturn(page);

        cacheWarmingJob.cacheWarmup();

        // THE regression assertion â€” old code never called set() at all
        verify(valueOperations).set(eq("url:hot1"), anyString(), any(Duration.class));
        verify(valueOperations).set(eq("url:hot2"), anyString(), any(Duration.class));
    }

    @Test
    void cacheWarmup_expiredUrl_isSkippedNotCached() {

        Page<Url> page = new PageImpl<>(List.of(expiredUrl(3L, "stale")));

        when(urlRepository.findByIsActiveTrueOrderByClickCountDesc(any(PageRequest.class))).thenReturn(page);

        cacheWarmingJob.cacheWarmup();

        verify(valueOperations, never()).set(eq("url:stale"), anyString(), any(Duration.class));
    }

    @Test
    void cacheWarmup_lockAlreadyHeld_skipsEntirely() {

        when(stringRedisTemplate.opsForValue().setIfAbsent(eq("lock:cache-warming"), anyString(), any(Duration.class)))
                .thenReturn(false);

        cacheWarmingJob.cacheWarmup();

        verify(urlRepository, never()).findByIsActiveTrueOrderByClickCountDesc(any());
        verify(stringRedisTemplate, never()).delete("lock:cache-warming");
    }

    @Test
    void cacheWarmup_alwaysReleasesLockEvenOnEmptyResult() {

        when(urlRepository.findByIsActiveTrueOrderByClickCountDesc(any(PageRequest.class)))
                .thenReturn(Page.empty());

        cacheWarmingJob.cacheWarmup();

        verify(stringRedisTemplate).delete("lock:cache-warming");
    }
}

