package com.minima-AI.ai.operations;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

// Shared HTTP ops for analytics-service.
// No CB/retry here - resilience at the tool layer.
@Service
@Slf4j
public class AnalyticsOperationsService {

    private final RestClient analyticsServiceClient;

    private final String apiPrefix;

    public AnalyticsOperationsService(
            @Qualifier("analyticsServiceClient") RestClient analyticsServiceClient,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.analyticsServiceClient = analyticsServiceClient;
        this.apiPrefix = apiPrefix;
    }

    // Matches analytics-service ClickStatsResponse field-for-field
    public record StatsResult(Long urlId, long totalClicks, String message) {}

    // Matches analytics-service TopUrlResponse field-for-field
    public record TopUrlResult(Long urlId, long clickCount) {}

    public StatsResult getStats(Long urlId, String userId) {

        log.debug("getStats userId: {}, urlId: {}", userId, urlId);

        return analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/{urlId}", urlId)
                .header("X-User-Id", userId)
                .retrieve()
                .body(StatsResult.class);
    }

    public List<TopUrlResult> getTopUrls(int limit, String userId) {

        log.debug("getTopUrls userId: {}, limit: {}", userId, limit);

        return analyticsServiceClient.get()
                .uri(apiPrefix + "/analytics/top?limit={limit}", limit)
                .header("X-User-Id", userId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<TopUrlResult>>() {});
    }
}

