package com.minima-AI.ai.summary;

import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import com.minima-AI.ai.summary.dto.SummaryResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SummaryService {

    private final ChatClient chatClient;

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final Resource summaryPrompt;

    private final Executor resilientOpsExecutor;

    public SummaryService(
            ChatClient chatClient,
            ResilientAnalyticsOps resilientAnalyticsOps,
            @Value("classpath:prompts/summary-service-prompt/summary-prompt.st")
            Resource summaryPrompt,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor
    ) {
        this.chatClient = chatClient;
        this.resilientAnalyticsOps = resilientAnalyticsOps;
        this.summaryPrompt = summaryPrompt;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @Bulkhead(name = "ai-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "ai-service", fallbackMethod = "summarizeFallback")
    @Retry(name = "ai-service")
    @TimeLimiter(name = "ai-service")
    public CompletableFuture<SummaryResponse> summarize(Long urlId, String userId) {

        return CompletableFuture.supplyAsync(() -> {

            log.info("Generating summary for urlId: {} userId: {}", urlId, userId);

            AnalyticsOperationsService.StatsResult stats;

            try {

                stats = resilientAnalyticsOps.getStats(urlId, userId).join();

            } catch (CompletionException ex) {

                log.error("Failed to fetch analytics while generating summary for urlId: {}", urlId, ex);

                throw ex;   // let CircuitBreaker/Retry/TimeLimiter see this as a real failure
            }

            // Circuit breaker open, timeout, retries exhausted, etc.
            if (stats == null) {

                log.warn("analytics-service unavailable while generating summary for urlId: {}", urlId);

                throw new IllegalStateException("analytics-service returned no data for urlId= " + urlId);
            }

            PromptTemplate template = new PromptTemplate(summaryPrompt);

            String prompt = template
                    .render(
                            Map.of(
                                    "clicks",
                                    stats.totalClicks()
                            )
                    );

            String text = chatClient.prompt().user(prompt).call().content();

            if (text == null || text.isBlank()) {
                throw new IllegalStateException("LLM returned empty summary for urlId=" + urlId);
            }

            log.debug("Summary generated for urlId={}: {}", urlId, text);

            return new SummaryResponse(text);

        }, resilientOpsExecutor);
    }


    public CompletableFuture<SummaryResponse> summarizeFallback(Long urlId, String userId, Throwable ex) {

        log.error("Summary unavailable for urlId={} userId={}", urlId, userId, ex);

        return CompletableFuture.completedFuture(
                new SummaryResponse("Performance statistics are temporarily unavailable. Please check back shortly.")
        );
    }
}

