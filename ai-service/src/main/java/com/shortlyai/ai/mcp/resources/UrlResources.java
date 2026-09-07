package com.minima-AI.ai.mcp.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlResources {

    private final ResilientUrlOps resilientUrlOps;

    private final ObjectMapper objectMapper;

    // Real record, not a hand-built string - Jackson handles escaping, so a
    // slug/originalUrl containing a quote can't produce broken JSON.
    public record UrlResource(String slug, String originalUrl, String shortUrl, long clickCount) {
    }

    @McpResource(
            uri = "shortly://url/{slug}",
            name = "url-details",
            title = "URL Details",
            description = "Full details (original URL, short URL, click count) for one shortened URL owned by the authenticated user.",
            mimeType = "application/json"
    )
    public String getUrl(
            @McpArg(name = "slug", description = "Short URL slug (e.g. abc123)", required = true)
            String slug
    ) {

        String userId = McpUserContext.get();

        log.info("MCP resource url-details userId: {}, slug: {}", userId, slug);

        UrlOperationsService.UrlDetails details = resilientUrlOps
                .getDetails(slug, userId)
                .join();

        if (details == null) {

            throw new IllegalArgumentException(
                    "No URL found for slug '%s'"
                            .formatted(slug)
            );
        }

        return writeJson(
                new UrlResource(
                        details.slug(),
                        details.originalUrl(),
                        details.shortUrl(),
                        details.clickCount()
                )
        );
    }

    @McpResource(
            uri = "shortly://urls",
            name = "url-list",
            title = "All My URLs",
            description = "All shortened URLs owned by the authenticated user.",
            mimeType = "application/json"
    )
    public String listUrls() {

        String userId = McpUserContext.get();

        log.info("MCP resource url-list userId: {}", userId);

        List<UrlResource> allUrls = resilientUrlOps
                .getAllForUser(userId)
                .join();

        // fallback returns null on real failure (CB open/timeout) - url-service
        // genuinely having zero URLs would come back as an empty list, not null
        if (allUrls == null) {

            log.warn("MCP resource url-list userId: {} - url-service unavailable", userId);

            throw new IllegalStateException("url-service is temporarily unavailable");
        }

        List<UrlResource> resources = allUrls.stream()
                .map(d ->
                        new UrlResource(
                                d.slug(),
                                d.originalUrl(),
                                d.shortUrl(),
                                d.clickCount()
                        )
                )
                .toList();

        return writeJson(resources);
    }

    // completion capability - ties to the shortly://url/{slug} resource template
    @McpComplete(uri = "shortly://url/{slug}")
    public List<String> completeSlug(String prefix) {

        String userId = McpUserContext.get();

        List<UrlResource> all = resilientUrlOps
                .getAllForUser(userId)
                .join();

        if (all == null) return List.of();

        return all.stream()
                .map(UrlResource::slug)
                .filter(s -> s.startsWith(prefix))
                .limit(10)
                .toList();
    }

    private String writeJson(Object value) {

        try {

            return objectMapper.writeValueAsString(value);

        } catch (JsonProcessingException e) {

            throw new IllegalStateException("Failed to serialize MCP resource payload", e);
        }
    }
}

