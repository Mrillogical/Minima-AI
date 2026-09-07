package com.minima-AI.url.common.config.cache;

import com.minima-AI.url.shortening.Url;
import com.minima-AI.url.shortening.UrlRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class CacheWarmingJob {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final long cacheTtlSeconds;

    private static final String WARM_LOCK_KEY = "lock:cache-warming";

    private static final String CACHE_PREFIX = "url:";

    // Cache separator format
    private static final String CACHE_SEP = "\u0000";

    public CacheWarmingJob(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            @Value("${url.cache-ttl-seconds}") long cacheTtlSeconds
    ) {
        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cacheWarmup() {

        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(WARM_LOCK_KEY, "locked", Duration.ofMinutes(5));

        if (!Boolean.TRUE.equals(acquired)) {

            log.info("Cache warming already running on another instance, skipping");

            return;
        }

        try {

            Page<Url> mostActiveUrls = urlRepository
                    .findByIsActiveTrueOrderByClickCountDesc(PageRequest.of(0, 100));

            Instant now = Instant.now();
            long warmedCount = 0;
            long skippedExpired = 0;

            for (Url url : mostActiveUrls) {

                if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(now)) {
                    skippedExpired++;
                    continue;
                }

                long expiresAtMs = url.getExpiresAt() == null
                        ? Long.MAX_VALUE
                        : url.getExpiresAt().toEpochMilli();

                stringRedisTemplate.opsForValue().set(
                        CACHE_PREFIX + url.getSlug(),
                        url.getId() + CACHE_SEP + url.getUserId() + CACHE_SEP
                                + expiresAtMs + CACHE_SEP + url.getOriginalUrl(),
                        Duration.ofSeconds(cacheTtlSeconds)
                );

                warmedCount++;
            }

            log.info("Cache warmed with {} URLs (skipped {} expired)", warmedCount, skippedExpired);

        } finally {

            stringRedisTemplate.delete(WARM_LOCK_KEY);
        }
    }
}

