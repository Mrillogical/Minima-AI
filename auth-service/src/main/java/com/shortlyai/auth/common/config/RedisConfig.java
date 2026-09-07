package com.minima-AI.auth.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// Configures Redis template â€” used to read/write refresh tokens
@Configuration
public class RedisConfig {

    // StringRedisTemplate â€” simplified template for String key-value pairs
    // RefreshTokens are strings â€” no need for generic RedisTemplate
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}


