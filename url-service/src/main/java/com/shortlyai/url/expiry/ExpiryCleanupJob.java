package com.minima-AI.url.expiry;

import com.minima-AI.url.shortening.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExpiryCleanupJob {

    private final UrlRepository urlRepository;

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 * * * *")
    @SchedulerLock(name = "urlExpiryCleanup", lockAtMostFor = "5m")
    @Transactional
    public void cleanupExpiredUrls() {

        log.info("Starting expired URLs cleanup..");

        Instant now = Instant.now();

        List<String> expiredSlugs = urlRepository.findExpiredSlugs(now);

        if (expiredSlugs.isEmpty()) {

            log.info("No expired URLs found, skipping");

            return;
        }

        int count = urlRepository.deactivateExpiredUrls(now);

        List<String> cacheKeys = expiredSlugs.stream().map(slug -> "url:" + slug).toList();

        TransactionSynchronizationManager
                .registerSynchronization(new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        stringRedisTemplate.delete(cacheKeys);
                    }
                });

        log.info("Expired URLs cleanup completed - deactivated {} URLs", count);
    }
}

