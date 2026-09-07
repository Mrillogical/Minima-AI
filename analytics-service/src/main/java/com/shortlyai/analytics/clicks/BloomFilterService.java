package com.minima-AI.analytics.clicks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private static final String BLOOM_KEY = "bloom:clicks";

    // Lua wraps BF command â€” Redis executes Lua, returns Long (1 or 0), never boolean
    private static final RedisScript<Long> BF_EXISTS_SCRIPT = RedisScript.of(
            "return redis.call('BF.EXISTS', KEYS[1], ARGV[1])",
            Long.class  // return type â€” Lua integers map to Long in Java
    );

    private static final RedisScript<Long> BF_ADD_SCRIPT = RedisScript.of(
            "return redis.call('BF.ADD', KEYS[1], ARGV[1])",
            Long.class
    );

    private final RedisTemplate<String, String> redisTemplate;

    public boolean isDuplicate(String fingerprint) {

        try {
            Long result = redisTemplate.execute(
                    BF_EXISTS_SCRIPT,
                    List.of(BLOOM_KEY),  // KEYS[1]
                    fingerprint          // ARGV[1]
            );

            // 1L = seen before (duplicate), 0L = new click
            return Long.valueOf(1L).equals(result);

        } catch (Exception e) {

            log.warn("Bloom filter check failed, allowing click through: {}", e.getMessage());

            return false;
        }
    }

    public void markSeen(String fingerprint) {

        try {
            redisTemplate.execute(
                    BF_ADD_SCRIPT,
                    List.of(BLOOM_KEY),  // KEYS[1]
                    fingerprint          // ARGV[1]
            );

        } catch (Exception e) {

            log.warn("Bloom filter markSeen failed: {}", e.getMessage());
        }
    }
}

