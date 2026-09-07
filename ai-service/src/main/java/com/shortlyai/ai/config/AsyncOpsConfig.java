package com.minima-AI.ai.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class AsyncOpsConfig {

    @Bean(name = "resilientOpsExecutor")
    public Executor resilientOpsExecutor() {

        ThreadFactory factory = Thread.ofVirtual()
                .name("resilient-ops-", 0)
                .factory();

        ExecutorService raw = Executors.newThreadPerTaskExecutor(factory);

        ContextSnapshotFactory snapshotFactory =
                ContextSnapshotFactory.builder()
                        .build();

        // propagates MDC Trace ID automatically
        // used for returning from CompletableFutures which runs on different virtual threads
        return ContextExecutorService.wrap(raw, snapshotFactory);
    }
}

