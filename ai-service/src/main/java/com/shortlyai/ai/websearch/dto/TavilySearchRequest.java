package com.minima-AI.ai.websearch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TavilySearchRequest(

        String query,

        @JsonProperty("search_depth")
        String searchDepth,

        @JsonProperty("include_answer")
        boolean includeAnswer,

        @JsonProperty("include_raw_content")
        boolean includeRawContent,

        @JsonProperty("max_results")
        int maxResults
) {}

