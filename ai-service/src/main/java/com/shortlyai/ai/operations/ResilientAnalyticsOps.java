package com.minima-AI.ai.operations;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class ResilientAnalyticsOps {

    private final AnalyticsOperationsService analyticsOps;

    private final Executor resilientOpsExecutor;

    public ResilientAnalyticsOps(
            AnalyticsOperationsService analyticsOps,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor
    ) {
        this.analyticsOps = analyticsOps;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @Bulkhead(name = "analytics-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getStatsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    public CompletableFuture<AnalyticsOperationsService.StatsResult> getStats(
            Long urlId,
            String userId
    ) {

        log.debug("Fetching URL stats for userId: {} urlId: {}", userId, urlId);

        return CompletableFuture.supplyAsync(() ->
                        analyticsOps.getStats(urlId, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<AnalyticsOperationsService.StatsResult> getStatsFallback(
            Long urlId,
            String userId,
            Throwable ex
    ) {

        log.error(
                "analytics-service is not available for urlId: {}, userId: {}. Please try after some time",
                urlId, userId, ex
        );

        return CompletableFuture.completedFuture(null);
    }

    @Bulkhead(name = "analytics-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "analytics-service", fallbackMethod = "getTopUrlsFallback")
    @Retry(name = "analytics-service")
    @TimeLimiter(name = "analytics-service")
    public CompletableFuture<List<AnalyticsOperationsService.TopUrlResult>> getTopUrls(
            int limit,
            String userId
    ) {

        log.debug("Fetching top URLs for userId: {} limit: {}", userId, limit);

        return CompletableFuture.supplyAsync(() ->
                        analyticsOps.getTopUrls(limit, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<List<AnalyticsOperationsService.TopUrlResult>> getTopUrlsFallback(
            int limit,
            String userId,
            Throwable ex
    ) {

        log.error(
                "analytics-service is unavailable for limit: {}, userId: {}. Failed to fetch your top URLs. Please try again later",
                limit, userId, ex
        );

        return CompletableFuture.completedFuture(null);
    }
}

