package com.minima-AI.gateway.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

// Binds rate-limit.routes.* from application.yaml
// Each service gets its own replenishRate + burstCapacity
@ConfigurationProperties(prefix = "rate-limit")
public record RouteRateLimitProperties(Map<String, RouteConfig> routes) {

    // All 3 fields required in yaml â€” records have no field defaults
    public record RouteConfig(
            int replenishRate,     // tokens added per second (sustained rate)
            int burstCapacity,     // max tokens in bucket (spike tolerance)
            int requestedTokens    // tokens consumed per request
    ) {}
}

