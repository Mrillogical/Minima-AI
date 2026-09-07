package com.minima-AI.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    @Bean
    RestClient urlServiceClient(
            @Value("${services.url-service.base-url}") String baseUrl
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(buildFactory())
                .build();
    }

    @Bean
    RestClient analyticsServiceClient(
            @Value("${services.analytics-service.base-url}") String baseUrl
    ) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(buildFactory())
                .build();
    }

    private SimpleClientHttpRequestFactory buildFactory() {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(CONNECT_TIMEOUT);

        factory.setReadTimeout(READ_TIMEOUT);

        return factory;
    }
}

