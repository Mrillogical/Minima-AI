package com.minima-AI.ai.classification;

import com.minima-AI.ai.classification.dto.ClassificationRequest;
import com.minima-AI.ai.classification.dto.ClassificationResponse;
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
public class ClassificationService {

    private final ChatClient chatClient;

    private final WebSearchTool webSearchTool;

    private final Executor resilientOpsExecutor;

    private final Resource classifyPrompt;

    public ClassificationService(
            ChatClient chatClient,
            WebSearchTool webSearchTool,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor,
            @Value("classpath:prompts/classification-service-prompt/classification-prompt.st")
            Resource classifyPrompt
    ) {
        this.chatClient = chatClient;
        this.webSearchTool = webSearchTool;
        this.resilientOpsExecutor = resilientOpsExecutor;
        this.classifyPrompt = classifyPrompt;
    }

    @Bulkhead(name = "ai-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "classification", fallbackMethod = "classificationFallback")
    @Retry(name = "classification")
    @TimeLimiter(name = "classification")
    public CompletableFuture<ClassificationResponse> classify(ClassificationRequest classificationRequest) {

        return CompletableFuture.supplyAsync(() -> {

                    ClassificationResponse result = chatClient.prompt()
                            .system(s ->
                                    s.text(classifyPrompt)
                                            .param("url", classificationRequest.url())
                            )
                            .user("Classify this URL")
                            .tools(webSearchTool)
                            .call()
                            .entity(ClassificationResponse.class);

                    log.info("Classified url: {} as: {}", classificationRequest.url(), result);

                    return result;
                },
                resilientOpsExecutor
        );
    }

    public CompletableFuture<ClassificationResponse> classificationFallback(ClassificationRequest request, Throwable t) {

        log.warn("Classification failed, url: {}, reason: {}", request.url(), t.getMessage());

        // null, not a fake "Unknown" response: a real LLM-judged "Unknown" and a
        // transient Groq outage used to look identical and both got published +
        // permanently persisted on the url row, with no way to retell them apart
        // or retry once Groq recovered. UrlCreatedEventListener treats null as
        // "don't publish anything, url stays unclassified for now".
        return CompletableFuture.completedFuture(null);
    }
}

