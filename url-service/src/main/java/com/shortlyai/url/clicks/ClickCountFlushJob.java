package com.minima-AI.url.clicks;

import com.minima-AI.url.shortening.UrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ClickCountFlushJob {

    private static final String PENDING_CLICKS_PREFIX = "clicks:pending:";

    private final StringRedisTemplate stringRedisTemplate;

    private final UrlRepository urlRepository;

    @Scheduled(fixedDelay = 30_000)
    @SchedulerLock(name = "clickCountFlush", lockAtMostFor = "1m")
    public void flushPendingClicks() {

        List<Long> ids = new ArrayList<>();
        List<Long> counts = new ArrayList<>();
        List<String> claimedKeys = new ArrayList<>(); // parallel to ids/counts - needed to restore on failure

        List<String> pendingKeys = stringRedisTemplate.execute(connection -> {

            List<String> keys = new ArrayList<>();

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(
                    ScanOptions.scanOptions()
                            .match(PENDING_CLICKS_PREFIX + "*")
                            .count(200)
                            .build()
            )) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return keys;
        }, true); // true = expose full connection for scanning, don't pipeline

        if (pendingKeys == null || pendingKeys.isEmpty()) {

            log.debug("Click flush: no pending click keys, skipping");

            return;
        }

        List<Object> pipelinedResults = stringRedisTemplate.executePipelined(
                (RedisCallback<Object>) connection -> {
                    for (String key : pendingKeys) {
                        connection.stringCommands().getDel(key.getBytes(StandardCharsets.UTF_8));
                    }
                    return null; // return value ignored in pipelined mode - replies come back via pipelinedResults
                }
        );

        for (int i = 0; i < pendingKeys.size(); i++) {

            String key = pendingKeys.get(i);
            Object rawResult = pipelinedResults.get(i);

            if (rawResult == null) {
                continue; // another instance's flush already claimed this key
            }

            try {

                long urlId = Long.parseLong(key.substring(PENDING_CLICKS_PREFIX.length()));
                long count = Long.parseLong((String) rawResult);

                ids.add(urlId);
                counts.add(count);
                claimedKeys.add(key);

            } catch (Exception e) {

                log.error("Click flush: skipping unparseable key '{}' value '{}': {}",
                        key, rawResult, e.getMessage());
            }
        }

        if (ids.isEmpty()) {

            log.debug("Click flush: all pending keys were already claimed or unparseable, skipping");

            return;
        }

        try {

            urlRepository.batchIncrementClickCounts(
                    ids.toArray(new Long[0]),
                    counts.toArray(new Long[0])
            );

            log.info(
                    "Click flush: batched {} URL(s), {} total clicks into Postgres",
                    ids.size(), counts.stream().mapToLong(Long::longValue).sum()
            );

        } catch (Exception e) {

            log.error("Click flush: DB batch increment failed for {} URL(s) - restoring counts to Redis for next cycle: {}",
                    ids.size(), e.getMessage(), e);

            stringRedisTemplate.executePipelined(
                    (RedisCallback<Object>) connection -> {
                        for (int i = 0; i < claimedKeys.size(); i++) {
                            connection.stringCommands().incrBy(
                                    claimedKeys.get(i).getBytes(StandardCharsets.UTF_8),
                                    counts.get(i)
                            );
                        }
                        return null;
                    }
            );
        }
    }
}

