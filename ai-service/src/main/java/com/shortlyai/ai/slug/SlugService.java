package com.minima-AI.ai.slug;

import com.minima-AI.ai.slug.dto.SlugRequest;
import com.minima-AI.ai.slug.dto.SlugResponse;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SlugService {

    private final ChatClient chatClient;

    private final Resource slugPrompt;

    private final Executor resilientOpsExecutor;

    public SlugService(
            ChatClient chatClient,
            @Value("classpath:prompts/slug-service-prompt/slug-prompt.st")
            Resource slugPrompt,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor
    ) {
        this.chatClient = chatClient;
        this.slugPrompt = slugPrompt;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @Bulkhead(name = "ai-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "ai-service", fallbackMethod = "suggestFallback")
    @Retry(name = "ai-service")
    @TimeLimiter(name = "ai-service")
    public CompletableFuture<SlugResponse> suggest(SlugRequest request) {

        return CompletableFuture.supplyAsync(() -> {

            log.info("Generating slug suggestions for url: {}", request.url());

            PromptTemplate template = new PromptTemplate(slugPrompt);

            String prompt = template
                    .render(
                            Map.of(
                                    "url", request.url(),
                                    "context", request.context() == null ? "" : request.context() // Map.of() throws NPE
                            )
                    );

            SlugResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .entity(SlugResponse.class);

            if (response == null) {
                throw new IllegalStateException("LLM returned unparseable response for slug suggestion");
            }

            return response;

        }, resilientOpsExecutor);
    }

    public CompletableFuture<SlugResponse> suggestFallback(SlugRequest request, Throwable ex) {

        log.error("Slug suggestion unavailable for url: {}", request.url(), ex);

        return CompletableFuture.completedFuture(new SlugResponse(List.of()));
    }
}

