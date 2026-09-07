package com.minima-AI.ai.slug;

import com.minima-AI.ai.slug.dto.SlugRequest;
import com.minima-AI.ai.slug.dto.SlugResponse;
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
import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SlugServiceTests {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    ChatClient.CallResponseSpec callResponseSpec;

    private final Executor directExecutor = Runnable::run;

    private final Resource prompt = new ByteArrayResource("Suggest slug for {url} ({context})".getBytes(StandardCharsets.UTF_8));

    SlugService slugService;

    @BeforeEach
    void setUp() {
        slugService = new SlugService(chatClient, prompt, directExecutor);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    @Test
    void suggest_withContext_returnsLlmSuggestions() {

        SlugResponse response = new SlugResponse(List.of("spring-boot-tips", "boot4-guide"));
        when(callResponseSpec.entity(SlugResponse.class)).thenReturn(response);

        SlugResponse result = slugService.suggest(new SlugRequest("https://example.com", "blog post")).join();

        assertThat(result.suggestions()).containsExactly("spring-boot-tips", "boot4-guide");
    }

    // regression test for the Map.of()-null NPE - context is documented as optional
    // on SlugRequest, so null MUST be a valid, non-throwing input here
    @Test
    void suggest_nullContext_doesNotThrow_treatsAsOptional() {

        SlugResponse response = new SlugResponse(List.of("no-context-slug"));
        when(callResponseSpec.entity(SlugResponse.class)).thenReturn(response);

        SlugResponse result = slugService.suggest(new SlugRequest("https://example.com", null)).join();

        assertThat(result.suggestions()).containsExactly("no-context-slug");
    }

    @Test
    void suggest_llmReturnsNull_throwsIllegalState() {

        when(callResponseSpec.entity(SlugResponse.class)).thenReturn(null);

        // use a non-null context here specifically so this test isolates the
        // "LLM returned null" path, not the context-null path (covered above)
        assertThatThrownBy(() ->
                slugService.suggest(new SlugRequest("https://example.com", "blog post")).join()
        ).hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggestFallback_returnsEmptySuggestions() {

        SlugResponse fallback = slugService.suggestFallback(
                new SlugRequest("https://example.com", "blog post"),
                new RuntimeException("down")
        ).join();

        assertThat(fallback.suggestions()).isEmpty();
    }
}

