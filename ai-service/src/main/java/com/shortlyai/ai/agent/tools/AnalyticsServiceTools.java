package com.minima-AI.ai.agent.tools;

import com.minima-AI.ai.operations.AnalyticsOperationsService;
import com.minima-AI.ai.operations.ResilientAnalyticsOps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceTools {

    private final ResilientAnalyticsOps resilientAnalyticsOps;

    @Tool(description = "Get click analytics for a shortened URL by its urlId")
    public String getUrlStats(
            @ToolParam(description = "numeric ID of the shortened URL") Long urlId,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getUrlStats userId: {} urlId: {}", userId, urlId);

        try {

            AnalyticsOperationsService.StatsResult stats =
                    resilientAnalyticsOps
                            .getStats(urlId, userId)
                            .join();

            if (stats == null) {
                return "Click stats for URL %d temporarily unavailable."
                        .formatted(urlId);
            }

            log.debug(
                    "getUrlStats urlId: {} totalClicks: {}",
                    urlId,
                    stats.totalClicks()
            );

            return "URL %d has %d total clicks"
                    .formatted(stats.urlId(), stats.totalClicks());

        } catch (Exception ex) {

            log.error(
                    "Failed to fetch stats for userId: {} urlId: {}",
                    userId,
                    urlId,
                    ex
            );

            return "Click stats for URL %d temporarily unavailable."
                    .formatted(urlId);
        }
    }

    @Tool(description = "Get the top performing URLs by click count for the current user")
    public String getTopUrls(
            @ToolParam(description = "how many top URLs to return") int limit,
            ToolContext toolContext
    ) {

        String userId = (String) toolContext.getContext().get("userId");

        log.info("Tool getTopUrls userId: {} limit: {}", userId, limit);

        try {

            List<AnalyticsOperationsService.TopUrlResult> topUrls =
                    resilientAnalyticsOps
                            .getTopUrls(limit, userId)
                            .join();

            if (topUrls == null) {
                return "Top URLs temporarily unavailable, analytics-service is down.";
            }

            if (topUrls.isEmpty()) {
                return "You don't have any URLs with recorded clicks yet.";
            }

            log.debug(
                    "getTopUrls userId: {} count: {}",
                    userId,
                    topUrls.size()
            );

            StringBuilder sb = new StringBuilder("Top URLs:\n");

            for (AnalyticsOperationsService.TopUrlResult url : topUrls) {
                sb.append("- urlId ")
                        .append(url.urlId())
                        .append(": ")
                        .append(url.clickCount())
                        .append(" clicks\n");
            }

            return sb.toString();

        } catch (Exception ex) {

            log.error(
                    "Failed to fetch top URLs for userId: {} limit: {}",
                    userId,
                    limit,
                    ex
            );

            return "Top URLs temporarily unavailable, analytics-service is down.";
        }
    }
}

