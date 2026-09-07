package com.minima-AI.analytics.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m") // if job hangs, release lock after 10 min
public class ShedLockConfig {

    // ShedLock uses Redis to store distributed locks
    // Prevents hourly rollup job from running on 2 pods simultaneously
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "analytics-service");
        //                                               ^^ key namespace in Redis
    }
}

