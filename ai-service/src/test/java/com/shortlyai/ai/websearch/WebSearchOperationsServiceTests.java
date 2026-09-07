package com.minima-AI.ai.websearch;

import com.minima-AI.ai.websearch.dto.TavilySearchRequest;
import com.minima-AI.ai.websearch.dto.TavilySearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSearchOperationsServiceTests {

    @Mock
    RestClient tavilyClient;

    @Mock
    RestClient.RequestBodyUriSpec bodyUriSpec;

    @Mock
    RestClient.RequestBodySpec bodySpec;

    @Mock
    RestClient.ResponseSpec responseSpec;

    WebSearchOperationsService webSearchOps;

    @BeforeEach
    void setUp() {

        webSearchOps = new WebSearchOperationsService(tavilyClient);

        when(tavilyClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri("/search")).thenReturn(bodySpec);
        when(bodySpec.body(any(TavilySearchRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void search_returnsFormattedSnippets() {

        var result = new TavilySearchResponse.Result("Example", "https://example.com", "some content");
        when(responseSpec.body(TavilySearchResponse.class))
                .thenReturn(new TavilySearchResponse(null, List.of(result)));

        List<String> snippets = webSearchOps.search("test query");

        assertThat(snippets).hasSize(1);
        assertThat(snippets.get(0)).contains("Example", "https://example.com", "some content");
    }

    @Test
    void search_nullResponse_returnsEmptyList() {

        when(responseSpec.body(TavilySearchResponse.class)).thenReturn(null);

        List<String> snippets = webSearchOps.search("test query");

        assertThat(snippets).isEmpty();
    }

    @Test
    void search_nullResults_returnsEmptyList() {

        when(responseSpec.body(TavilySearchResponse.class))
                .thenReturn(new TavilySearchResponse("some answer", null));

        List<String> snippets = webSearchOps.search("test query");

        assertThat(snippets).isEmpty();
    }
}

