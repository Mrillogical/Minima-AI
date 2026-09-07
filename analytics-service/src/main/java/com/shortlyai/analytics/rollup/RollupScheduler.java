package com.minima-AI.analytics.rollup;

import com.minima-AI.analytics.clicks.ClickEventRepository;
import com.minima-AI.analytics.clicks.ClickHourly;
import com.minima-AI.analytics.clicks.ClickHourlyRepository;
import com.minima-AI.analytics.clicks.TopUrlResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RollupScheduler {

    private final ClickEventRepository clickEventRepository;
    private final ClickHourlyRepository clickHourlyRepository;

    // Runs at 5 minutes past every hour e.g. 14:05, 15:05
    // Aggregates the PREVIOUS hour's clicks into click_hourly table
    @Scheduled(cron = "0 5 * * * *")
    @SchedulerLock(
            name = "analytics_hourly_rollup",
            lockAtLeastFor = "4m",
            lockAtMostFor = "9m"
    )
    @Transactional
    public void rollupPreviousHour() {

        Instant hourTo = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant hourFrom = hourTo.minus(1, ChronoUnit.HOURS);

        log.info("Starting hourly rollup for window {} -> {}", hourFrom, hourTo);

        List<TopUrlResponse> counts = clickEventRepository.countGroupedByUrlIdBetween(hourFrom, hourTo);

        if (counts.isEmpty()) {

            log.info("Hourly rollup complete. No activity this window.");

            return;
        }

        List<Long> urlIds = counts.stream()
                .map(TopUrlResponse::urlId)
                .toList();


        Map<Long, ClickHourly> existingByUrlId = clickHourlyRepository
                .findByUrlIdInAndHour(urlIds, hourFrom)
                .stream()
                .collect(Collectors.toMap(ClickHourly::getUrlId, Function.identity()));

        List<ClickHourly> rowsToSave = new ArrayList<>();

        for (TopUrlResponse count : counts) {

            ClickHourly row = existingByUrlId.getOrDefault(
                    count.urlId(),
                    new ClickHourly(count.urlId(), hourFrom, 0L)
            );

            row.setClickCount(count.clickCount());
            rowsToSave.add(row);
        }

        clickHourlyRepository.saveAll(rowsToSave);

        log.info("Hourly rollup complete. Processed {} URLs", counts.size());
    }
}

