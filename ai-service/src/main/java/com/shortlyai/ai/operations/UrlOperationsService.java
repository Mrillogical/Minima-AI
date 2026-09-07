package com.minima-AI.ai.operations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.minima-AI.ai.mcp.resources.UrlResources;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

// Shared HTTP ops for url-service
// No CB/retry here - resilience handled by ResilientUrlOps
@Service
@Slf4j
public class UrlOperationsService {

    private final RestClient urlServiceClient;

    private final String apiPrefix;

    public UrlOperationsService(
            @Qualifier("urlServiceClient") RestClient urlServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.urlServiceClient = urlServiceClient;
        this.apiPrefix = apiPrefix;
    }

    // url-service ShortenResponse has 9 fields (id, slug, shortUrl, originalUrl,
    // userId, isCustom, clickCount, expiresAt, createdAt).
    // We only need 3 - ignoreUnknown = true prevents UnrecognizedPropertyException.
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShortenResult(Long id, String slug, String shortUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UrlDetails(Long id, String slug, String originalUrl, String shortUrl, long clickCount) {}

    public ShortenResult shorten(String originalUrl, String userId) {

        record ShortenRequest(String originalUrl) {}

        log.debug("shorten userId: {}, url: {}", userId, originalUrl);

        return urlServiceClient.post()
                .uri(apiPrefix + "/urls")
                .header("X-User-Id", userId)
                .body(new ShortenRequest(originalUrl))
                .retrieve()
                .body(ShortenResult.class);
    }

    public UrlDetails getDetails(String slug, String userId) {

        log.debug("getDetails userId: {}, slug: {}", userId, slug);

        return urlServiceClient.get()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);
    }

    public UrlDetails getDetailsById(Long urlId, String userId) {

        log.debug("getDetails userId: {}, urlId: {}", userId, urlId);

        return urlServiceClient.get()
                .uri(apiPrefix + "/urls/id/{id}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(UrlDetails.class);
    }

    public void delete(String slug, String userId) {

        log.debug("delete userId: {}, slug: {}", userId, slug);

        urlServiceClient.delete()
                .uri(apiPrefix + "/urls/slug/{slug}", slug)
                .header("X-User-Id", userId)
                .retrieve()
                .toBodilessEntity();
    }


    public List<UrlResources.UrlResource> getAllUrlsForUser(String userId) {

        log.info("Fetching all URLs for userId: {}", userId);

        PageResponse<UrlResources.UrlResource> page = urlServiceClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(apiPrefix + "/urls")
                        .queryParam("size", 1000)
                        .build()
                )
                .header("X-User-Id", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return page != null ? page.content() : List.of();
    }

    private record PageResponse<T>(List<T> content) {}
}

