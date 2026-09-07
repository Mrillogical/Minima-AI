package com.minima-AI.url.common.config;

import io.micrometer.context.ContextExecutorService;
import io.micrometer.context.ContextSnapshotFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Dedicated executor for redirect-path side effects (click-count write,
// click event publish)
@Configuration
public class AsyncTrackingConfig {

    @Bean
    public Executor clickTrackingExecutor() {

        ExecutorService raw = Executors.newVirtualThreadPerTaskExecutor();

        ContextSnapshotFactory snapshotFactory =
                ContextSnapshotFactory.builder()
                        .build();

        // propagates MDC Trace ID automatically
        return ContextExecutorService.wrap(raw, snapshotFactory);
    }
}

