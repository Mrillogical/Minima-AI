package com.minima-AI.ai.summary;

import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import com.minima-AI.ai.summary.dto.SummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SummaryServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    ResilientAnalyticsOps resilientAnalyticsOps;

    private final Executor directExecutor = Runnable::run;

    private final Resource prompt = new ByteArrayResource("Summarize {clicks} clicks".getBytes(StandardCharsets.UTF_8));

    SummaryService summaryService;

    @BeforeEach
    void setUp() {
        summaryService = new SummaryService(chatClient, resilientAnalyticsOps, prompt, directExecutor);
    }

    @Test
    void summarize_success_returnsLlmText() {

        when(resilientAnalyticsOps.getStats(1L, "user-1"))
                .thenReturn(CompletableFuture.completedFuture(
                        new AnalyticsOperationsService.StatsResult(1L, 50L, "ok")
                ));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Your link has 50 clicks, trending up.");

        SummaryResponse result = summaryService.summarize(1L, "user-1").join();

        assertThat(result.summary()).contains("50 clicks");
    }

    @Test
    void summarize_statsNull_throwsIllegalState() {

        when(resilientAnalyticsOps.getStats(1L, "user-1"))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertThatThrownBy(() -> summaryService.summarize(1L, "user-1").join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void summarize_analyticsFetchThrowsCompletionException_rethrows() {

        CompletableFuture<AnalyticsOperationsService.StatsResult> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("analytics down"));

        when(resilientAnalyticsOps.getStats(1L, "user-1")).thenReturn(failed);

        assertThatThrownBy(() -> summaryService.summarize(1L, "user-1").join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("analytics down");
    }

    @Test
    void summarize_llmReturnsBlank_throwsIllegalState() {

        when(resilientAnalyticsOps.getStats(1L, "user-1"))
                .thenReturn(CompletableFuture.completedFuture(
                        new AnalyticsOperationsService.StatsResult(1L, 50L, "ok")
                ));

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("   ");

        assertThatThrownBy(() -> summaryService.summarize(1L, "user-1").join())
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void summarizeFallback_returnsFallbackText() {

        SummaryResponse fallback = summaryService.summarizeFallback(1L, "user-1", new RuntimeException("down")).join();

        assertThat(fallback.summary()).contains("temporarily unavailable");
    }
}

