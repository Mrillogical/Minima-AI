package com.minima-AI.analytics.clicks;

import com.minima-AI.analytics.events.UrlClickedEvent;
import com.minima-AI.analytics.events.UrlCreatedEvent;
import com.minima-AI.analytics.events.UrlDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickServiceImpl implements ClickService {

    private final ClickEventRepository clickEventRepository;

    private final BloomFilterService bloomFilterService;

    private final StringRedisTemplate redisTemplate;

    private final ClickHourlyRepository clickHourlyRepository;

    private final UrlOwnerRepository urlOwnerRepository;

    private static final String OWNER_KEY_PREFIX = "url:owner:";

    @Transactional
    public void processClick(UrlClickedEvent event) {
        Instant clickedAt = event.clickedAt() != null ? event.clickedAt() : Instant.now();
        String fingerprint = event.urlId() + ":" + event.ipHash() + ":" + (clickedAt.getEpochSecond() / 60);

        if (bloomFilterService.isDuplicate(fingerprint)) {
            log.debug("Duplicate click detected for slug: {}, skipping", event.slug());
            return;
        }

        ClickEvent clickEvent = ClickEvent.from(event);
        clickEventRepository.save(clickEvent);

        String redisKey = "clicks:realtime:" + event.urlId();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {

                bloomFilterService.markSeen(fingerprint);

                redisTemplate.opsForValue().increment(redisKey);
            }
        });

        log.debug("Processed click for slug: {} urlId: {}", event.slug(), event.urlId());
    }

    @Transactional(readOnly = true)
    public long getTotalClicks(Long urlId, UUID userId) {

        if (!isOwner(urlId, userId)) {
            throw new AccessDeniedException("URL not found");
        }

        String redisKey = "clicks:realtime:" + urlId;

        String cached = redisTemplate.opsForValue().get(redisKey);

        if (cached != null) {

            try {

                return Long.parseLong(cached);

            } catch (NumberFormatException _) {

                log.warn("Corrupt redis counter for urlId {}, falling back to DB", urlId);
            }
        }

        return clickEventRepository.countByUrlId(urlId);
    }

    @Transactional
    public void initializeCounter(UrlCreatedEvent event) {

        // durable ownership row - Redis is fast-path, this is the real
        // source of truth the DB fallback in isOwner() now queries
        if (!urlOwnerRepository.existsById(event.urlId())) {

            urlOwnerRepository.save(new UrlOwner(event.urlId(), event.userId()));
        }

        String redisKey = "clicks:realtime:" + event.urlId();
        String ownerKey = OWNER_KEY_PREFIX + event.urlId();
        String userId = event.userId().toString();

        TransactionSynchronizationManager.registerSynchronization(

                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        redisTemplate.opsForValue().setIfAbsent(redisKey, "0");

                        // ownership cache, so stats endpoints work before the first click exists
                        redisTemplate.opsForValue().set(ownerKey, userId);
                    }
                }
        );

        log.debug("Initialized click counter for slug: {} urlId: {}", event.slug(), event.urlId());
    }

    @Transactional
    public void deleteClickData(UrlDeletedEvent event) {

        clickEventRepository.deleteByUrlId(event.id());
        clickHourlyRepository.deleteByUrlId(event.id());
        urlOwnerRepository.deleteByUrlId(event.id());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            @Override
            public void afterCommit() {

                redisTemplate.delete("clicks:realtime:" + event.id());

                redisTemplate.delete(OWNER_KEY_PREFIX + event.id());
            }
        });

        log.info("Deleted click data for slug: {} urlId: {}", event.slug(), event.id());
    }

    @Transactional(readOnly = true)
    public List<HourlyBreakdownResponse> getHourlyBreakdown(Long urlId, UUID userId, int hours) {

        if (!isOwner(urlId, userId)) {
            throw new AccessDeniedException("URL not found");
        }

        List<ClickHourly> clicks = clickHourlyRepository
                .findByUrlIdAndSince(urlId, Instant.now().minus(hours, ChronoUnit.HOURS));

        return clicks.stream()
                .map(row ->
                        new HourlyBreakdownResponse(
                                row.getHour(),
                                row.getClickCount()
                        )
                )
                .toList();
    }

    // Redis-first ownership check, DB fallback for cache misses / pre-fix rows
    private boolean isOwner(Long urlId, UUID userId) {

        String cachedOwner = redisTemplate.opsForValue().get(OWNER_KEY_PREFIX + urlId);

        if (cachedOwner != null) {
            return cachedOwner.equals(userId.toString());
        }

        return urlOwnerRepository.existsByUrlIdAndUserId(urlId, userId);
    }

    // Queries raw click_events grouped by urlId. Returns top N by click count
    @Transactional(readOnly = true)
    public List<TopUrlResponse> getTopUrls(UUID userId, int limit) {

        return clickEventRepository.findTopUrlsByUserId(
                userId,
                PageRequest.of(
                        0,
                        limit
                )
        );
    }
}

