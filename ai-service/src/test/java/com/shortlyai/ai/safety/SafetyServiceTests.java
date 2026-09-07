package com.minima-AI.ai.safety;

import com.minima-AI.ai.safety.dto.SafetyCheckRequest;
import com.minima-AI.ai.safety.dto.SafetyCheckResponse;
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
class SafetyServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    WebSearchTool webSearchTool;

    @Mock
    Resource safetyPrompt;

    private Executor resilientOpsExecutor;

    private SafetyService safetyService;

    @BeforeEach
    void setUp() {

        // Execute CompletableFuture synchronously
        resilientOpsExecutor = Runnable::run;

        safetyService = new SafetyService(
                chatClient,
                webSearchTool,
                resilientOpsExecutor,
                safetyPrompt
        );

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.tools(any())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void checkSafety_unsafeUrl_returnsHighRisk() {

        SafetyCheckResponse response =
                new SafetyCheckResponse(false, "HIGH", "Phishing indicators present");

        when(callResponseSpec.entity(SafetyCheckResponse.class))
                .thenReturn(response);

        SafetyCheckResponse result = safetyService.checkSafety(
                new SafetyCheckRequest("http://verify-paypal-account-login.xyz")
        ).join();

        assertThat(result.safe()).isFalse();
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.reasoning()).isEqualTo("Phishing indicators present");
    }

    @Test
    void checkSafety_safeUrl_returnsLowRisk() {

        SafetyCheckResponse response =
                new SafetyCheckResponse(true, "LOW", "No issues detected");

        when(callResponseSpec.entity(SafetyCheckResponse.class))
                .thenReturn(response);

        SafetyCheckResponse result = safetyService.checkSafety(
                new SafetyCheckRequest("https://github.com")
        ).join();

        assertThat(result.safe()).isTrue();
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.reasoning()).isEqualTo("No issues detected");
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkSafety_bindsPromptAndUrlToSystemSpec() {

        when(callResponseSpec.entity(SafetyCheckResponse.class))
                .thenReturn(new SafetyCheckResponse(true, "LOW", "Looks fine"));

        safetyService.checkSafety(
                new SafetyCheckRequest("https://example.com")
        ).join();

        ArgumentCaptor<Consumer<ChatClient.PromptSystemSpec>> captor =
                ArgumentCaptor.forClass(Consumer.class);

        verify(requestSpec).system(captor.capture());

        ChatClient.PromptSystemSpec systemSpec =
                mock(ChatClient.PromptSystemSpec.class);

        when(systemSpec.text(any(Resource.class))).thenReturn(systemSpec);
        when(systemSpec.param(anyString(), any())).thenReturn(systemSpec);

        captor.getValue().accept(systemSpec);

        verify(systemSpec).text(safetyPrompt);
        verify(systemSpec).param("url", "https://example.com");

        verify(requestSpec).user("Check this URL");
        verify(requestSpec, never()).user(any(Consumer.class));
    }

    @Test
    void checkSafety_registersWebSearchTool() {

        when(callResponseSpec.entity(SafetyCheckResponse.class))
                .thenReturn(new SafetyCheckResponse(true, "LOW", "Looks fine"));

        safetyService.checkSafety(
                new SafetyCheckRequest("https://example.com")
        ).join();

        verify(requestSpec).tools(webSearchTool);
    }

    @Test
    void checkSafety_llmThrows_propagatesException() {

        when(callResponseSpec.entity(SafetyCheckResponse.class))
                .thenThrow(new RuntimeException("LLM timeout"));

        assertThatThrownBy(() ->
                safetyService.checkSafety(
                        new SafetyCheckRequest("https://example.com")
                ).join()
        )
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasRootCauseMessage("LLM timeout");
    }

    @Test
    void safetyCheckFallback_returnsUnsafeForManualReview() {

        SafetyCheckRequest request =
                new SafetyCheckRequest("https://example.com");

        SafetyCheckResponse response = safetyService
                .safetyCheckFallback(
                        request,
                        new RuntimeException("LLM unavailable")
                )
                .join();

        assertThat(response.safe()).isFalse();
        assertThat(response.riskLevel()).isEqualTo("MEDIUM");
        assertThat(response.reasoning())
                .contains("manual review");
    }
}

