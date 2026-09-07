package com.minima-AI.auth.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class UnverifiedAccountCleanupJob {

    private final UserRepository userRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "unverifiedAccountCleanup", lockAtMostFor = "5m")
    @Transactional
    public void deleteUnverifiedUsers() {

        log.info("Starting unverified users cleanup..");

        userRepository.deleteByVerifiedFalseAndCreatedAtBefore(
                Instant.now()
                        .minus(7, ChronoUnit.DAYS)
        );

        log.info("Finished unverified users cleanup");
    }
}


