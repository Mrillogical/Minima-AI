package com.minima-AI.analytics.cleanup;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Slf4j
@RequiredArgsConstructor
public class RealTimeCleanupJob {

    private final StringRedisTemplate stringRedisTemplate;

    @Scheduled(fixedDelay = 30 * 60 * 1000) // every 30 min
    @SchedulerLock(name = "realtime_redis_cleanup", lockAtLeastFor = "1m", lockAtMostFor = "5m")
    public void cleanupZeroCounters() {

        log.info("Starting zero URL click counters cleanup..");

        List<String> keys = new ArrayList<>();

        ScanOptions options = ScanOptions.scanOptions()
                .match("clicks:realtime:*")
                .count(500)
                .build();

        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection();

             Cursor<byte[]> cursor = connection.scan(options)) {

            while (cursor.hasNext()) {

                keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
            }
        }

        if (keys.isEmpty()) {

            log.info("No realtime keys found");

            return;
        }

        // single round trip for all values
        List<String> values = stringRedisTemplate.opsForValue()
                .multiGet(keys);

        Set<String> keysToDelete = new HashSet<>();

        for (int i = 0; i < keys.size(); i++) {

            if ("0".equals(values.get(i))) {
                keysToDelete.add(keys.get(i));
            }
        }

        if (keysToDelete.isEmpty()) {

            log.info("No zero-count keys to clean up");

            return;
        }

        stringRedisTemplate.delete(keysToDelete);

        log.info("Cleaned up {} zero-count realtime keys", keysToDelete.size());
    }
}

