package com.minima-AI.url.shortening;

import com.minima-AI.url.common.dto.ShortenRequest;
import com.minima-AI.url.common.dto.ShortenResponse;
import com.minima-AI.url.common.exception.DuplicateSlugException;
import com.minima-AI.url.common.exception.UrlNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ShorteningServiceImpl implements ShorteningService {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    private final UrlEventPublisher urlEventPublisher;

    private final Executor clickTrackingExecutor;

    private final String baseDomain;

    private final long defaultExpiryDays;

    private final long cacheTtlSeconds;

    public ShorteningServiceImpl(
            UrlRepository urlRepository,
            StringRedisTemplate stringRedisTemplate,
            UrlEventPublisher urlEventPublisher,
            @Value("${url.base-domain}") String baseDomain,
            @Value("${url.default-expiry-days}") long defaultExpiryDays,
            @Value("${url.cache-ttl-seconds}") long cacheTtlSeconds,
            Executor clickTrackingExecutor) {

        this.urlRepository = urlRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.urlEventPublisher = urlEventPublisher;
        this.clickTrackingExecutor = clickTrackingExecutor;
        this.baseDomain = baseDomain;
        this.defaultExpiryDays = defaultExpiryDays;
        this.cacheTtlSeconds = cacheTtlSeconds;
    }

    private static final String CACHE_PREFIX = "url:";

    // \u0000 (null byte)
    // URL is stored LAST so split("\u0000", 4) never cuts into it.
    // Format: "urlId\u0000userId\u0000expiresAtEpochMs\u0000originalUrl"
    private static final String CACHE_SEP = "\u0000";

    private static final String PENDING_CLICKS_PREFIX = "clicks:pending:";

    @Override
    @Transactional
    public ShortenResponse shorten(ShortenRequest request, UUID userId) {

        log.info("URL shortening request for userId: {}", userId);

        boolean isCustom =
                request.customSlug() != null &&
                        !request.customSlug().isBlank();

        if (isCustom && urlRepository.existsBySlug(request.customSlug())) {
            throw new DuplicateSlugException("This slug is already taken");
        }

        String slug = isCustom
                ? request.customSlug()
                : "tmp" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Url url = Url.builder()
                .originalUrl(request.originalUrl())
                .userId(userId)
                .isCustom(isCustom)
                .expiresAt(Instant.now().plus(
                        request.expiryDays() == null ? defaultExpiryDays : request.expiryDays(),
                        ChronoUnit.DAYS)
                )
                .slug(slug)
                .build();

        Url savedUrl = urlRepository.save(url);

        if (!isCustom) {

            String generatedSlug;

            int attempts = 0;

            do {

                generatedSlug = Base62.generateRandomSlug();

                attempts++;

                if (attempts > 5) {

                    throw new IllegalStateException(
                            "Failed to generate unique slug after " + attempts + " attempts");
                }

            } while (urlRepository.existsBySlug(generatedSlug));

            savedUrl.setSlug(generatedSlug);

            savedUrl = urlRepository.save(savedUrl);
        }

        long finalExpiresAtMs = savedUrl.getExpiresAt() == null
                ? Long.MAX_VALUE : savedUrl.getExpiresAt().toEpochMilli();

        Url finalSavedUrl = savedUrl;

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                stringRedisTemplate.opsForValue().set(
                                        CACHE_PREFIX + finalSavedUrl.getSlug(),
                                        finalSavedUrl.getId() + CACHE_SEP + finalSavedUrl.getUserId() + CACHE_SEP
                                                + finalExpiresAtMs + CACHE_SEP + finalSavedUrl.getOriginalUrl(),
                                        Duration.ofSeconds(cacheTtlSeconds));
                            }
                        }
                );

        urlEventPublisher.publishCreated(savedUrl);

        log.info("Created short-url '{}' for userId: {}", savedUrl.getSlug(), userId);

        return mapToResponse(savedUrl);
    }

    @Override
    public String resolve(String slug, HttpServletRequest request) {

        String ipHash = sha256(request.getRemoteAddr());
        String userAgent = request.getHeader("User-Agent");
        String referer = request.getHeader("Referer");

        String cached = stringRedisTemplate.opsForValue()
                .get(CACHE_PREFIX + slug);

        // cache hit
        if (cached != null) {

            String[] parts = cached.split(CACHE_SEP, 4);

            if (parts.length < 4) {

                // stale pre-migration entry (old 3-part format) - evict it and
                // fall through to the cache-miss path below instead of returning,
                // so this request still gets served correctly from the DB
                log.warn("Stale cache format for slug '{}', evicting and falling back to DB", slug);

                stringRedisTemplate.delete(CACHE_PREFIX + slug);

            } else {

                log.debug("Cache hit for slug: {}", slug);

                Long urlId = Long.parseLong(parts[0]);

                UUID userId = UUID.fromString(parts[1]);

                long expiresAtMs = Long.parseLong(parts[2]);

                String originalUrl = parts[3];

                if (Instant.ofEpochMilli(expiresAtMs).isBefore(Instant.now())) {

                    log.info("Cached slug '{}' has expired, evicting and treating as not found", slug);

                    stringRedisTemplate.delete(CACHE_PREFIX + slug);

                    throw new UrlNotFoundException("URL not found");
                }

                // Fire-and-forget: Kafka publish + click-count UPDATE happen off this thread
                dispatchClickTracking(urlId, slug, userId, ipHash, userAgent, referer);

                return originalUrl;   // only returns here - the valid, non-stale, non-expired case
            }
        }

        log.debug("Cache miss for slug. Fetching from DB: {}", slug);

        // cache miss
        Url shortUrl = urlRepository.findBySlugAndIsActiveTrueAndExpiresAtAfter(
                        slug,
                        Instant.now()
                )
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        long expiresAtMs = shortUrl.getExpiresAt() == null
                ? Long.MAX_VALUE
                : shortUrl.getExpiresAt().toEpochMilli();

        stringRedisTemplate.opsForValue().set(
                CACHE_PREFIX + slug,
                shortUrl.getId() +
                        CACHE_SEP +
                        shortUrl.getUserId() +
                        CACHE_SEP +
                        expiresAtMs +
                        CACHE_SEP +
                        shortUrl.getOriginalUrl(),
                Duration.ofSeconds(cacheTtlSeconds)
        );

        dispatchClickTracking(
                shortUrl.getId(),
                slug,
                shortUrl.getUserId(),
                ipHash,
                userAgent,
                referer
        );

        return shortUrl.getOriginalUrl();
    }

    // Runs on clickTrackingExecutor (virtual-thread-per-task), never on the request thread
    private void dispatchClickTracking(
            Long urlId,
            String slug,
            UUID ownerId,
            String ipHash,
            String userAgent,
            String referer
    ) {

        clickTrackingExecutor.execute(() -> {

            try {

                urlEventPublisher.publishClick(urlId, slug, ownerId, ipHash, userAgent, referer);

                stringRedisTemplate.opsForValue()
                        .increment(PENDING_CLICKS_PREFIX + urlId);

            } catch (Exception e) {

                log.error("Async click tracking failed for slug '{}' urlId {}: {}",
                        slug, urlId, e.getMessage());
            }
        });
    }

    @Override
    @Transactional
    public void delete(Long id, UUID userId) {

        // fetch URL
        Url url = urlRepository.findByIdAndUserIdAndIsActiveTrue(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // fetch noOfRows
        int rowsDeleted = urlRepository.softDeleteByIdAndUserId(id, userId, Instant.now());

        // if no rows deleted, throw
        if (rowsDeleted == 0) {
            throw new UrlNotFoundException("URL not found");
        }

        log.info("Soft-deleted URL slug: {} by userId: {}", url.getSlug(), userId);

        // cache evict
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.delete(CACHE_PREFIX + url.getSlug());
                    }
                }
        );

        urlEventPublisher.publishDeleted(url);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortenResponse getUrl(Long id, UUID userId) {

        Url url = urlRepository.findByIdAndUserIdAndIsActiveTrue(id, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        return mapToResponse(url);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortenResponse> getUserUrls(UUID userId, Pageable pageable) {

        return urlRepository.findByUserIdAndIsActiveTrueOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortenResponse getUrlBySlug(String slug, UUID userId) {

        Url url = urlRepository
                .findBySlugAndUserIdAndIsActiveTrue(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found with slug: " + slug)
                );

        return mapToResponse(url);
    }

    @Override
    @Transactional
    public void deleteUrl(String slug, UUID userId) {

        // fetch - throws UrlNotFoundException if not found (GlobalExceptionHandler -> 404)
        Url url = urlRepository.findBySlugAndUserIdAndIsActiveTrue(slug, userId)
                .orElseThrow(() -> new UrlNotFoundException("URL slug " + slug + " not found"));

        // Soft-delete, same as delete(id, userId). Never hard-delete here:
        // analytics-service keeps click history keyed off this row, and a
        // hard delete would leave dangling references / break click stats.
        int rowsDeleted = urlRepository.softDeleteBySlugAndUserId(slug, userId, Instant.now());

        if (rowsDeleted == 0) {
            throw new UrlNotFoundException("URL slug " + slug + " not found");
        }

        // evict Redis cache
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.delete(CACHE_PREFIX + slug);
                    }
                }
        );

        urlEventPublisher.publishDeleted(url);

        log.info("Soft-deleted URL slug: {} urlId: {} userId: {}", slug, url.getId(), userId);
    }

    private ShortenResponse mapToResponse(Url u) {

        return new ShortenResponse(
                u.getId(),
                u.getSlug(),
                baseDomain + "/r/" + u.getSlug(),
                u.getOriginalUrl(),
                u.getUserId(),
                u.isCustom(),
                u.getClickCount(),
                u.getExpiresAt(),
                u.getCreatedAt()
        );
    }

    private String sha256(String input) {

        try {

            var digest = java.security.MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            var hex = new StringBuilder();

            for (byte b : hash) hex.append(String.format("%02x", b));

            return hex.toString();

        } catch (Exception _) {
            return "unknown";
        }
    }
}

