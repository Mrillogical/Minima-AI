package com.minima-AI.analytics.clicks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BloomFilterServiceTests {

    @Mock
    RedisTemplate<String, String> redisTemplate;

    BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        bloomFilterService = new BloomFilterService(redisTemplate);
    }

    @Test
    void isDuplicate_bloomReturnsOne_returnsTrue() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);

        boolean result = bloomFilterService.isDuplicate("urlId:iphash:minutebucket");

        assertThat(result).isTrue();
    }

    @Test
    void isDuplicate_bloomReturnsZero_returnsFalse() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any())).thenReturn(0L);

        boolean result = bloomFilterService.isDuplicate("fresh-fingerprint");

        assertThat(result).isFalse();
    }

    @Test
    void isDuplicate_redisThrows_failsOpenAndAllowsClickThrough() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        // Must never propagate â€” a Bloom filter outage should not block click tracking
        boolean result = bloomFilterService.isDuplicate("fingerprint");

        assertThat(result).isFalse();
    }

    @Test
    void markSeen_executesAddScriptWithFingerprint() {

        bloomFilterService.markSeen("some-fingerprint");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("bloom:clicks")), eq("some-fingerprint"));
    }

    @Test
    void markSeen_redisThrows_swallowsExceptionSilently() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RuntimeException("timeout"));

        // must not throw â€” click processing already saved to Postgres by this point
        bloomFilterService.markSeen("fingerprint");
    }
}

