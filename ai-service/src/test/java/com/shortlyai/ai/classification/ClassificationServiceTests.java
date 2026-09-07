package com.minima-AI.ai.classification;

import com.minima-AI.ai.classification.dto.ClassificationRequest;
import com.minima-AI.ai.classification.dto.ClassificationResponse;
import com.minima-AI.ai.websearch.WebSearchTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClassificationServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    WebSearchTool webSearchTool;

    @Mock
    Resource classifyPrompt;

    private final Executor resilientOpsExecutor = Runnable::run;

    private ClassificationService classificationService;

    @BeforeEach
    void setUp() {

        classificationService = new ClassificationService(
                chatClient,
                webSearchTool,
                resilientOpsExecutor,
                classifyPrompt
        );

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void classify_success_returnsCategory() {

        ClassificationResponse response = new ClassificationResponse(
                "Example Site",
                "Tech",
                0.9,
                List.of("java", "backend")
        );

        when(callResponseSpec.entity(ClassificationResponse.class))
                .thenReturn(response);

        ClassificationResponse result = classificationService.classify(
                new ClassificationRequest("https://example.com")
        ).join();

        assertThat(result.title()).isEqualTo("Example Site");
        assertThat(result.category()).isEqualTo("Tech");
        assertThat(result.confidence()).isEqualTo(0.9);
        assertThat(result.tags()).containsExactly("java", "backend");
    }

    @Test
    @SuppressWarnings("unchecked")
    void classify_bindsPromptAndUrlToSystemSpec() {

        when(callResponseSpec.entity(ClassificationResponse.class))
                .thenReturn(new ClassificationResponse(
                        "Example",
                        "Tech",
                        0.8,
                        List.of()
                ));

        classificationService.classify(
                new ClassificationRequest("https://example.com")
        ).join();

        ArgumentCaptor<Consumer<ChatClient.PromptSystemSpec>> captor =
                ArgumentCaptor.forClass(Consumer.class);

        verify(requestSpec).system(captor.capture());

        ChatClient.PromptSystemSpec systemSpec =
                mock(ChatClient.PromptSystemSpec.class);

        when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
        when(systemSpec.param(anyString(), any())).thenReturn(systemSpec);

        captor.getValue().accept(systemSpec);

        verify(systemSpec).text(classifyPrompt);
        verify(systemSpec).param("url", "https://example.com");

        verify(requestSpec).user("Classify this URL");
        verify(requestSpec, never()).user(any(Consumer.class));
    }

    @Test
    void classify_registersWebSearchTool() {

        when(callResponseSpec.entity(ClassificationResponse.class))
                .thenReturn(new ClassificationResponse(
                        "Example",
                        "Tech",
                        0.8,
                        List.of()
                ));

        classificationService.classify(
                new ClassificationRequest("https://example.com")
        ).join();

        verify(requestSpec).tools(webSearchTool);
    }

    @Test
    void classify_llmThrows_propagatesException() {

        when(callResponseSpec.entity(ClassificationResponse.class))
                .thenThrow(new RuntimeException("LLM timeout"));

        assertThatThrownBy(() ->
                classificationService.classify(
                        new ClassificationRequest("https://example.com")
                ).join()
        )
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("LLM timeout");
    }

    @Test
    void classificationFallback_returnsNullSoCallerSkipsPublishing() {

        ClassificationRequest request =
                new ClassificationRequest("https://example.com");

        ClassificationResponse fallback = classificationService
                .classificationFallback(
                        request,
                        new RuntimeException("LLM unavailable")
                )
                .join();

        // null, not a fake "Unknown" - lets UrlCreatedEventListener tell a
        // genuine LLM-judged Unknown apart from Groq simply being unreachable,
        // instead of persisting a false verdict with no path to retry it later.
        assertThat(fallback).isNull();
    }
}

