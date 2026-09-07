package com.minima-AI.ai.mcp.prompts;

import com.minima-AI.ai.mcp.auth.McpUserContext;
import com.minima-AI.ai.mcp.resources.UrlResources;
import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import com.minima-AI.ai.operations.ResilientUrlOps;
import com.minima-AI.ai.operations.UrlOperationsService;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UrlPrompts {

    private final ResilientUrlOps resilientUrlOps;

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    @McpPrompt(
            name = "analyze-url",
            title = "Analyze URL",
            description = "Analyzes a shortened URL's real performance and flags any security concerns."
    )
    public GetPromptResult analyze(
            @McpArg(name = "slug", description = "Short URL slug (e.g. abc123)", required = true)
            String slug
    ) {

        String userId = McpUserContext.get();

        log.info("MCP prompt analyze-url userId: {}, slug: {}", userId, slug);

        UrlOperationsService.UrlDetails details = resilientUrlOps
                .getDetails(slug, userId)
                .join();

        if (details == null) {
            throw new IllegalArgumentException(
                    "No URL found for slug '%s'"
                            .formatted(slug)
            );
        }

        AnalyticsOperationsService.StatsResult stats = resilientAnalyticsOps
                .getStats(details.id(), userId)
                .join();

        String prompt = getString(stats, details);

        return new GetPromptResult(
                "URL Analysis",
                List.of(new PromptMessage(Role.USER, new TextContent(prompt))),
                null
        );
    }

    private static @NonNull String getString(
            AnalyticsOperationsService.StatsResult stats,
            UrlOperationsService.UrlDetails details
    ) {

        long totalClicks = stats != null ? stats.totalClicks() : details.clickCount();

        // Real data embedded, not a blind "go analyze this" - the LLM gets actual
        // facts to reason over instead of having to call tools first to get them.
         return  """
                Analyze this shortened URL:
                
                Slug: %s
                Original destination: %s
                Short URL: %s
                Total clicks: %d
                
                Include:
                - Popularity assessment (is %d clicks high/low/typical for a shortened link?)
                - Security concerns (does the original destination look suspicious, e.g. URL shorteners chained together, known malicious patterns, mismatched domain vs likely intent?)
                - Recommendations (should this link be promoted more, retired, or investigated further?)
                """
                 .formatted(
                         details.slug(),
                         details.originalUrl(),
                         details.shortUrl(),
                         totalClicks,
                         totalClicks
                 );
    }

    @McpComplete(prompt = "analyze-url")
    public List<String> completeSlugForAnalyze(String prefix) {

        String userId = McpUserContext.get();

        List<UrlResources.UrlResource> all =
                resilientUrlOps.getAllForUser(userId)
                        .join();

        if (all == null) return List.of();

        return all.stream()
                .map(UrlResources.UrlResource::slug)
                .filter(s -> s.startsWith(prefix))
                .limit(10)
                .toList();
    }
}

