package com.minima-AI.ai.websearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSearchToolTests {

    @Mock
    ResilientWebSearchOps resilientWebSearchOps;

    WebSearchTool webSearchTool;

    @BeforeEach
    void setUp() {
        webSearchTool = new WebSearchTool(resilientWebSearchOps);
    }

    @Test
    void search_returnsSynchronousList_notFuture() {

        List<String> expected = List.of("Title: Example\nContent: test");
        when(resilientWebSearchOps.search("scam reports example.com"))
                .thenReturn(CompletableFuture.completedFuture(expected));

        List<String> result = webSearchTool.search("scam reports example.com");

        assertThat(result).isEqualTo(expected);
        verify(resilientWebSearchOps).search("scam reports example.com");
    }

    @Test
    void search_emptyResilientResult_returnsEmptyList() {

        when(resilientWebSearchOps.search("obscure query"))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        List<String> result = webSearchTool.search("obscure query");

        assertThat(result).isEmpty();
    }
}

