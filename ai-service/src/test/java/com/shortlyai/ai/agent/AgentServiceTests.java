package com.minima-AI.ai.agent;

import com.minima-AI.ai.agent.dto.AgentResponse;
import com.minima-AI.ai.agent.tools.AnalyticsServiceTools;
import com.minima-AI.ai.agent.tools.UrlServiceTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    UrlServiceTools urlServiceTools;

    @Mock
    AnalyticsServiceTools analyticsServiceTools;

    @Mock
    ChatMemory chatMemory;

    private final Executor directExecutor = Runnable::run;

    private final Resource agentPrompt = new ByteArrayResource("You are minima-AI agent".getBytes(StandardCharsets.UTF_8));

    AgentService agentService;

    @BeforeEach
    void setUp() {

        agentService = new AgentService(
                chatClient,
                urlServiceTools,
                analyticsServiceTools,
                agentPrompt,
                directExecutor,
                chatMemory
        );

        when(chatClient.prompt()).thenReturn(requestSpec);

        when(requestSpec.system(any(Resource.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any(), any())).thenReturn(requestSpec);
        when(requestSpec.toolContext(anyMap())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void chat_success_returnsReplyAndPassesUserIdInToolContext() {

        when(callResponseSpec.content()).thenReturn("Here's your short URL.");

        AgentResponse result = agentService.chat("user-1", "shorten https://example.com").join();

        assertThat(result.reply()).isEqualTo("Here's your short URL.");
        verify(requestSpec).toolContext(Map.of("userId", "user-1"));
    }

    @Test
    void chatFallback_returnsGenericUnavailableMessage() {

        AgentResponse fallback = agentService.chatFallback("user-1", "hi", new RuntimeException("down")).join();

        assertThat(fallback.reply()).contains("temporarily unable to respond");
    }
}

