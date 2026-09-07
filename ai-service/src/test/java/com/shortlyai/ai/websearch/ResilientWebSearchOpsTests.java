package com.minima-AI.ai.websearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResilientWebSearchOpsTests {

    @Mock
    WebSearchOperationsService webSearchOps;

    private final Executor directExecutor = Runnable::run;

    ResilientWebSearchOps resilientWebSearchOps;

    @BeforeEach
    void setUp() {
        resilientWebSearchOps = new ResilientWebSearchOps(webSearchOps, directExecutor);
    }

    @Test
    void search_delegatesAndReturnsResult() {

        when(webSearchOps.search("brave test")).thenReturn(List.of("snippet 1", "snippet 2"));

        List<String> result = resilientWebSearchOps.search("brave test").join();

        assertThat(result).containsExactly("snippet 1", "snippet 2");
    }

    @Test
    void search_emptyUpstreamResult_returnsEmptyList() {

        when(webSearchOps.search("no results")).thenReturn(List.of());

        List<String> result = resilientWebSearchOps.search("no results").join();

        assertThat(result).isEmpty();
    }

    @Test
    void searchFallback_returnsEmptyList_onFailure() {

        List<String> result = resilientWebSearchOps
                .searchFallback("brave test", new RuntimeException("Tavily 503"))
                .join();

        assertThat(result).isEmpty();
    }
}

