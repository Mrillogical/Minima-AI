package com.minima-AI.ai.agent;

import com.minima-AI.ai.agent.dto.AgentResponse;
import com.minima-AI.ai.agent.tools.AnalyticsServiceTools;
import com.minima-AI.ai.agent.tools.UrlServiceTools;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class AgentService {

    private final ChatClient chatClient;

    private final UrlServiceTools urlServiceTools;

    private final AnalyticsServiceTools analyticsServiceTools;

    private final Resource agentPrompt;

    private final Executor resilientOpsExecutor;

    private final ChatMemory chatMemory;

    public AgentService(
            ChatClient chatClient,
            UrlServiceTools urlServiceTools,
            AnalyticsServiceTools analyticsServiceTools,
            @Value("classpath:prompts/agent-service-prompt/agent-prompt.st")
            Resource agentPrompt,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor,
            ChatMemory chatMemory
    ) {
        this.chatClient = chatClient;
        this.urlServiceTools = urlServiceTools;
        this.analyticsServiceTools = analyticsServiceTools;
        this.agentPrompt = agentPrompt;
        this.resilientOpsExecutor = resilientOpsExecutor;
        this.chatMemory = chatMemory;
    }

    @Bulkhead(name = "ai-agent", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "ai-agent", fallbackMethod = "chatFallback")
    @Retry(name = "ai-agent")
    @TimeLimiter(name = "ai-agent")
    public CompletableFuture<AgentResponse> chat(String userId, String message) {

        return CompletableFuture.supplyAsync(() -> {

            log.info("Agent chat request userId: {}, message: {}", userId, message);

            String reply = chatClient.prompt()
                    .system(agentPrompt)
                    .advisors(advisor ->
                            advisor.advisors(
                                            MessageChatMemoryAdvisor
                                                    .builder(chatMemory)
                                                    .build()
                                    )
                                    .param(ChatMemory.CONVERSATION_ID, userId)
                    )
                    .user(message)
                    .tools(urlServiceTools, analyticsServiceTools)
                    .toolContext(Map.of("userId", userId))   // passed to @Tool methods, not seen by LLM
                    .call()
                    .content();

            log.debug("Agent reply userId: {}: {}", userId, reply);

            return new AgentResponse(reply);

        }, resilientOpsExecutor);
    }

    public CompletableFuture<AgentResponse> chatFallback(
            String userId,
            String message,
            Throwable ex
    ) {

        log.error("Agent chat unavailable for userId: {}", userId, ex);

        return CompletableFuture.completedFuture(
                new AgentResponse("Sorry, I'm temporarily unable to respond. Please try again shortly.")
        );
    }
}

