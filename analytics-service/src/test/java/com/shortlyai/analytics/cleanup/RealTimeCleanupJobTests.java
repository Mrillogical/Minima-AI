package com.minima-AI.analytics.cleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeCleanupJobTests {

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    RedisConnectionFactory connectionFactory;

    @Mock
    RedisConnection redisConnection;

    @Mock
    Cursor<byte[]> cursor;

    @Mock
    ValueOperations<String, String> valueOperations;

    RealTimeCleanupJob job;

    @BeforeEach
    void setUp() {

        job = new RealTimeCleanupJob(stringRedisTemplate);

        when(stringRedisTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scan(any(ScanOptions.class))).thenReturn(cursor);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void deletesOnly_zeroCountKeys() {

        when(cursor.hasNext()).thenReturn(true, true, false);
        when(cursor.next()).thenReturn(
                "clicks:realtime:1".getBytes(StandardCharsets.UTF_8),
                "clicks:realtime:2".getBytes(StandardCharsets.UTF_8));

        when(valueOperations.multiGet(List.of("clicks:realtime:1", "clicks:realtime:2")))
                .thenReturn(Arrays.asList("0", "5"));

        job.cleanupZeroCounters();

        verify(stringRedisTemplate).delete(Set.of("clicks:realtime:1"));
    }

    @Test
    void noZeroCountKeys_skipsDeleteEntirely() {

        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("clicks:realtime:9".getBytes(StandardCharsets.UTF_8));

        when(valueOperations.multiGet(anyList())).thenReturn(List.of("12"));

        job.cleanupZeroCounters();

        verify(stringRedisTemplate, never()).delete((Set<String>) any());
    }

    @Test
    void keyExpiredBetweenScanAndGet_skipsSafely() {

        when(cursor.hasNext()).thenReturn(true, false);
        when(cursor.next()).thenReturn("clicks:realtime:3".getBytes(StandardCharsets.UTF_8));

        when(valueOperations.multiGet(anyList())).thenReturn(Arrays.asList((String) null));

        job.cleanupZeroCounters();

        verify(stringRedisTemplate, never()).delete((Set<String>) any());
    }

    @Test
    void noKeysMatched_skipsMultiGetAndDelete() {

        when(cursor.hasNext()).thenReturn(false);

        job.cleanupZeroCounters();

        verify(valueOperations, never()).multiGet(anyList());
        verify(stringRedisTemplate, never()).delete((Set<String>) any());
    }
}

