package com.minima-AI.auth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {

        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("audit-vt-");

        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(25);

        return executor;
    }

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {

        SimpleAsyncTaskExecutor executor =
                new SimpleAsyncTaskExecutor("email-vt-");

        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(25);

        return executor;
    }
}

