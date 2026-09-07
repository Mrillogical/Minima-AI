package com.minima-AI.ai.safety;

import com.minima-AI.ai.safety.dto.SafetyCheckRequest;
import com.minima-AI.ai.safety.dto.SafetyCheckResponse;
import com.minima-AI.ai.websearch.WebSearchTool;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SafetyService {

    private final ChatClient chatClient;

    private final WebSearchTool webSearchTool;

    private final Executor resilientOpsExecutor;

    private final Resource safetyPrompt;

    public SafetyService(
            ChatClient chatClient,
            WebSearchTool webSearchTool,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor,
            @Value("classpath:prompts/safety-service-prompt/safety-prompt.st")
            Resource safetyPrompt
    ) {
        this.chatClient = chatClient;
        this.webSearchTool = webSearchTool;
        this.resilientOpsExecutor = resilientOpsExecutor;
        this.safetyPrompt = safetyPrompt;
    }

    @Bulkhead(name = "ai-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "safetyCheck", fallbackMethod = "safetyCheckFallback")
    @Retry(name = "safetyCheck")
    @TimeLimiter(name = "safetyCheck")
    public CompletableFuture<SafetyCheckResponse> checkSafety(SafetyCheckRequest safetyCheckRequest) {

        return CompletableFuture.supplyAsync(() -> {

                    SafetyCheckResponse result = chatClient.prompt()
                            .system(s -> s
                                    .text(safetyPrompt)
                                    .param("url", safetyCheckRequest.url())
                            )
                            .user("Check this URL")
                            .tools(webSearchTool)
                            .call()
                            .entity(SafetyCheckResponse.class);

                    log.info("Safety check completed for url: {}, safe: {}, riskLevel: {}",
                            safetyCheckRequest.url(), result.safe(), result.riskLevel());

                    return result;
                },
                resilientOpsExecutor
        );
    }

    public CompletableFuture<SafetyCheckResponse> safetyCheckFallback(
            SafetyCheckRequest request,
            Throwable t
    ) {

        log.warn("Safety check failed, defaulting to unsafe for manual review, url: {}, reason: {}", request.url(), t.getMessage());

        // fail closed on infra failure
        return CompletableFuture.completedFuture(
                new SafetyCheckResponse(false, "MEDIUM", "AI check unavailable - manual review needed")
        );
    }
}

