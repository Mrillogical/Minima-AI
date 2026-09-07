package com.minima-AI.ai.websearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebSearchConfig {

    private final String baseUrl;

    private final String apiKey;

    public WebSearchConfig(
            @Value("${tavily.api.base-url}")
            String baseUrl,
            @Value("${tavily.api.key}")
            String apiKey
    ) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    @Bean(name = "tavilyClient")
    public RestClient tavilySearchClient() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(3000);  // 3s connect
        factory.setReadTimeout(25000);    // 25s read - under TimeLimiter's 30s

        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE)
                .requestFactory(factory)
                .build();
    }
}

