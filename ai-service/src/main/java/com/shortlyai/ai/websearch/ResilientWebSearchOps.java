package com.minima-AI.ai.websearch;

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
public class ResilientWebSearchOps {

    private final WebSearchOperationsService webSearchOps;

    private final Executor resilientOpsExecutor;

    public ResilientWebSearchOps(
            WebSearchOperationsService webSearchOps,
            @Qualifier("resilientOpsExecutor")
            Executor resilientOpsExecutor
    ) {
        this.webSearchOps = webSearchOps;
        this.resilientOpsExecutor = resilientOpsExecutor;
    }

    @Bulkhead(name = "webSearch", type = Bulkhead.Type.SEMAPHORE)
    @CircuitBreaker(name = "webSearch", fallbackMethod = "searchFallback")
    @Retry(name = "webSearch")
    @TimeLimiter(name = "webSearch")
    public CompletableFuture<List<String>> search(String query) {

        return CompletableFuture.supplyAsync(() ->
                        webSearchOps.search(query)
                , resilientOpsExecutor);
    }

    public CompletableFuture<List<String>> searchFallback(String query, Throwable ex) {

        log.warn("Tavily unavailable, query: {}, reason: {}", query, ex.getMessage());

        return CompletableFuture.completedFuture(List.of());
    }
}

