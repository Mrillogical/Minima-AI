package com.minima-AI.auth.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @SchedulerLock(name = "refreshTokenCleanup", lockAtMostFor = "5m")
    @Transactional
    public void cleanupExpiredTokens() {

        log.info("Refresh tokens cleanup started..");

        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());

        log.info("Finished expired refresh token cleanup");
    }
}


