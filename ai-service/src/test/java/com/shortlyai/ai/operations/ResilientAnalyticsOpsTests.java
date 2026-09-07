package com.minima-AI.ai.operations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResilientAnalyticsOpsTests {

    @Mock
    AnalyticsOperationsService analyticsOps;

    private final Executor directExecutor = Runnable::run;

    ResilientAnalyticsOps resilientAnalyticsOps;

    @BeforeEach
    void setUp() {
        resilientAnalyticsOps = new ResilientAnalyticsOps(analyticsOps, directExecutor);
    }

    @Test
    void getStats_delegatesAndReturnsResult() throws Exception {

        AnalyticsOperationsService.StatsResult expected =
                new AnalyticsOperationsService.StatsResult(1L, 10L, "ok");

        when(analyticsOps.getStats(1L, "user-1")).thenReturn(expected);

        CompletableFuture<AnalyticsOperationsService.StatsResult> result =
                resilientAnalyticsOps.getStats(1L, "user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void getStatsFallback_returnsNull_neverThrows() throws Exception {

        CompletableFuture<AnalyticsOperationsService.StatsResult> fallback =
                resilientAnalyticsOps.getStatsFallback(1L, "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }

    @Test
    void getTopUrls_delegatesAndReturnsResult() throws Exception {

        List<AnalyticsOperationsService.TopUrlResult> expected =
                List.of(new AnalyticsOperationsService.TopUrlResult(1L, 5L));

        when(analyticsOps.getTopUrls(5, "user-1")).thenReturn(expected);

        CompletableFuture<List<AnalyticsOperationsService.TopUrlResult>> result =
                resilientAnalyticsOps.getTopUrls(5, "user-1");

        assertThat(result.get()).isEqualTo(expected);
    }

    @Test
    void getTopUrlsFallback_returnsNull_neverThrows() throws Exception {

        CompletableFuture<List<AnalyticsOperationsService.TopUrlResult>> fallback =
                resilientAnalyticsOps.getTopUrlsFallback(5, "user-1", new RuntimeException("down"));

        assertThat(fallback.get()).isNull();
        assertThat(fallback.isCompletedExceptionally()).isFalse();
    }
}

