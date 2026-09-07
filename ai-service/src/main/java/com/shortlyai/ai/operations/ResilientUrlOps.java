package com.minima-AI.ai.operations;

import com.minima-AI.ai.mcp.resources.UrlResources;
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
public class ResilientUrlOps {

    private final UrlOperationsService urlOps;

    private final Executor resilientOpsExecutor;

    public ResilientUrlOps(
            UrlOperationsService urlOps,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor
    ) {
        this.urlOps = urlOps;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @Bulkhead(name = "url-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "url-service", fallbackMethod = "shortenFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.ShortenResult> shorten(
            String originalUrl,
            String userId
    ) {

        log.debug("Shortening URL for userId: {} url: {}", userId, originalUrl);

        return CompletableFuture.supplyAsync(() ->
                        urlOps.shorten(originalUrl, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.ShortenResult> shortenFallback(
            String originalUrl,
            String userId,
            Throwable ex
    ) {

        log.error("url-service is not available. Cannot shorten '{}' for user: {}. Please try again later", originalUrl, userId, ex);

        return CompletableFuture.completedFuture(null);
    }

    @Bulkhead(name = "url-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.UrlDetails> getDetails(
            String slug,
            String userId
    ) {

        log.debug("Fetching URL details for userId: {} slug: {}", userId, slug);

        return CompletableFuture.supplyAsync(() ->
                        urlOps.getDetails(slug, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsFallback(
            String slug,
            String userId,
            Throwable ex
    ) {

        log.error("url-service is not available while fetching slug: {} for user {}. Please try again later", slug, userId, ex);

        return CompletableFuture.completedFuture(null);
    }

    @Bulkhead(name = "url-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "url-service", fallbackMethod = "deleteFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<Boolean> delete(String slug, String userId) {

        log.debug("Deleting URL for userId: {} slug: {}", userId, slug);

        return CompletableFuture.supplyAsync(() -> {
                    urlOps.delete(slug, userId);
                    return true;
                },
                resilientOpsExecutor
        );
    }

    public CompletableFuture<Boolean> deleteFallback(String slug, String userId, Throwable ex) {

        log.error("url-service unavailable - could not delete slug: {} for user: {}", slug, userId, ex);

        return CompletableFuture.completedFuture(false);
    }

    @Bulkhead(name = "url-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "url-service", fallbackMethod = "getDetailsByIdFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsById(
            Long urlId,
            String userId
    ) {

        log.info("Fetching URL details for userId: {} ", userId);

        return CompletableFuture.supplyAsync(() ->
                        urlOps.getDetailsById(urlId, userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<UrlOperationsService.UrlDetails> getDetailsByIdFallback(
            Long urlId,
            String userId,
            Throwable ex
    ) {

        log.error("url-service unavailable - could not fetch URL with ID: {} for user: {}", urlId, userId, ex);

        return CompletableFuture.completedFuture(null);
    }

    @Bulkhead(name = "url-service", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "url-service", fallbackMethod = "getAllForUserFallback")
    @Retry(name = "url-service")
    @TimeLimiter(name = "url-service")
    public CompletableFuture<List<UrlResources.UrlResource>> getAllForUser(String userId) {

        log.info("Fetching all URLs for userId: {} ", userId);

        return CompletableFuture.supplyAsync(() ->
                        urlOps.getAllUrlsForUser(userId),
                resilientOpsExecutor
        );
    }

    public CompletableFuture<List<UrlResources.UrlResource>> getAllForUserFallback(String userId, Throwable ex) {

        log.error("url-service unavailable - could not fetch all URLs for userId: {}", userId, ex);

        return CompletableFuture.completedFuture(null);
    }
}

