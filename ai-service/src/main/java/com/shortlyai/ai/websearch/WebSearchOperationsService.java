package com.minima-AI.ai.websearch;

import com.minima-AI.ai.websearch.dto.TavilySearchRequest;
import com.minima-AI.ai.websearch.dto.TavilySearchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Slf4j
public class WebSearchOperationsService {

    private final RestClient tavilySearchClient;

    public WebSearchOperationsService(
            @Qualifier("tavilyClient") RestClient tavilySearchClient
    ) {
        this.tavilySearchClient = tavilySearchClient;
    }

    public List<String> search(String query) {

        log.info("Searching Tavily: {}", query);

        TavilySearchResponse response = tavilySearchClient
                .post()
                .uri("/search")
                .body(new TavilySearchRequest(query, "basic", true, false, 3))
                .retrieve()
                .body(TavilySearchResponse.class);

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results()
                .stream()
                .map(r -> """
                                Title: %s
                                URL: %s
                                Content: %s
                                """.formatted(
                                r.title(),
                                r.url(),
                                r.content()
                        )
                )
                .toList();
    }
}

