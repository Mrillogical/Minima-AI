package com.minima-AI.ai.mcp.tools;

import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.concurrent.CompletionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class McpAnalyticsTools {

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    private final ResilientUrlOps resilientUrlOps;

    public record UrlStatsPayload(String slug, long totalClicks) {}

    public record TopUrlPayload(long urlId, long clickCount) {}

    private String authenticatedUserIdOrNull() {
        return McpUserContext.get();
    }

    private CallToolResult internalAuthError(String toolName) {

        log.error("MCP {} - no authenticated userId in context, filter misconfigured", toolName);

        return CallToolResult.builder()
                .addTextContent("Internal error - request could not be completed.")
                .isError(true)
                .build();
    }

    @McpTool(name = "get-url-stats", description = """
            Returns the total click count for a shortened URL by its slug. Use this
            for quick click-count checks. If you also need the original destination
            URL, call get_url_details instead - it returns both in one call, making
            a separate get_url_stats call unnecessary in that case.
            """)
    public CallToolResult getUrlStats(
            @McpToolParam(description = "The short slug of the URL (e.g. 'abc123')", required = true)
            String slug
    ) {

        String userId = authenticatedUserIdOrNull();

        if (userId == null) return internalAuthError("get-url-stats");

        log.info("MCP tool get-url-stats invoked for userId: {}, slug: {}", userId, slug);

        try {

            UrlOperationsService.UrlDetails details = resilientUrlOps
                    .getDetails(slug, userId)
                    .join();

            if (details == null) {
                return CallToolResult.builder()
                        .addTextContent("Could not find a URL with slug '%s'.".formatted(slug))
                        .isError(true)
                        .build();
            }

            AnalyticsOperationsService.StatsResult stats = resilientAnalyticsOps
                    .getStats(details.id(), userId)
                    .join();

            if (stats == null) {

                return CallToolResult.builder()
                        .addTextContent("Click stats for '%s' temporarily unavailable.".formatted(slug))
                        .isError(true)
                        .build();
            }

            return CallToolResult.builder()
                    .addTextContent("URL '%s' has %d total clicks".formatted(slug, stats.totalClicks()))
                    .structuredContent(new UrlStatsPayload(slug, stats.totalClicks()))
                    .build();

        } catch (CompletionException e) {

            if (e.getCause() instanceof HttpClientErrorException httpEx) {

                log.warn("MCP get-url-stats 4xx userId: {}, slug: {}, status: {}", userId, slug, httpEx.getStatusCode());

                return CallToolResult.builder()
                        .addTextContent("Could not retrieve stats for '%s': %s".formatted(slug, httpEx.getStatusText()))
                        .isError(true)
                        .build();
            }
            throw e;
        }
    }

    @McpTool(name = "get-top-urls", description = """ 
            Returns the user's best-performing shortened URLs, ranked by total click
            count, highest first. Use this for "what's my most popular/top link"
            type requests, or when the user wants an overview rather than one specific
            URL. Each result includes the slug, original URL, shortened URL and click
            count - no follow-up call needed for basic info.
            """)
    public CallToolResult getTopUrls(
            @McpToolParam(description = "how many top URLs to return")
            int limit
    ) {

        String userId = authenticatedUserIdOrNull();

        if (userId == null) return internalAuthError("get-top-urls");

        if (limit < 1 || limit > 50) {

            return CallToolResult.builder()
                    .addTextContent("limit must be between 1 and 50.")
                    .isError(true)
                    .build();
        }

        log.info("Tool getTopUrls userId: {} limit: {}", userId, limit);

        try {

            List<AnalyticsOperationsService.TopUrlResult> topUrls =
                    resilientAnalyticsOps.getTopUrls(limit, userId).join();

            if (topUrls == null) {

                return CallToolResult.builder()
                        .addTextContent("Top URLs temporarily unavailable, analytics-service is down.")
                        .isError(true)
                        .build();
            }

            if (topUrls.isEmpty()) {

                return CallToolResult.builder()
                        .addTextContent("You don't have any URLs with recorded clicks yet.")
                        .structuredContent(List.<TopUrlPayload>of())
                        .build();
            }

            log.debug("getTopUrls userId: {} count: {}", userId, topUrls.size());

            StringBuilder sb = new StringBuilder("Top URLs:\n");

            List<TopUrlPayload> payload = topUrls.stream()
                    .map(url -> {
                        sb.append("- urlId ")
                                .append(url.urlId())
                                .append(": ")
                                .append(url.clickCount())
                                .append(" clicks\n");

                        return new TopUrlPayload(url.urlId(), url.clickCount());
                    })
                    .toList();

            return CallToolResult.builder()
                    .addTextContent(sb.toString())
                    .structuredContent(payload)
                    .build();

        } catch (Exception ex) {

            log.error("Failed to fetch top URLs for userId: {} limit: {}", userId, limit, ex);

            return CallToolResult.builder()
                    .addTextContent("Top URLs temporarily unavailable, analytics-service is down.")
                    .isError(true)
                    .build();
        }
    }
}

