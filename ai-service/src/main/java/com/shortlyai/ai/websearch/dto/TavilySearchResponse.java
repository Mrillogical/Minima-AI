package com.minima-AI.ai.websearch.dto;

import java.util.List;

public record TavilySearchResponse(

        String answer,

        List<Result> results
) {

    public record Result(
            String title,
            String url,
            String content
    ) {}
}

