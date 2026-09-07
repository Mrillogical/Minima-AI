package com.minima-AI.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RateLimitConfig {

    // RedisRateLimiter constructed from the "default" route config in yaml
    // Per-service configs loaded into the bean's internal map
    // isAllowed(routeId, key) looks up routeId â†’ picks correct rate config
    @Bean
    public RedisRateLimiter redisRateLimiter(RouteRateLimitProperties props) {

        // "default" entry in yaml drives the constructor fallback
        RouteRateLimitProperties.RouteConfig def =
                props.routes().getOrDefault("default",
                        new RouteRateLimitProperties.RouteConfig(5, 10, 1));

        RedisRateLimiter limiter = new RedisRateLimiter(
                def.replenishRate(),
                def.burstCapacity(),
                def.requestedTokens()
        );

        // Register per-service configs into the bean's internal ConcurrentHashMap
        // isAllowed() looks up this map by routeId to pick replenishRate/burstCapacity
        props.routes().forEach((routeId, cfg) -> {
            if (!routeId.equals("default")) {
                limiter.getConfig().put(routeId, new RedisRateLimiter.Config()
                        .setReplenishRate(cfg.replenishRate())
                        .setBurstCapacity(cfg.burstCapacity())
                        .setRequestedTokens(cfg.requestedTokens()));
            }
        });

        return limiter;
    }

    // KeyResolver picks bucket key: authenticated â†’ userId, anonymous â†’ IP
    // RateLimitFilter PREPENDS routeId to this key to avoid cross-service bucket bleed
    // e.g. "auth:user:uuid" vs "url:user:uuid" â€” completely separate buckets
    @Bean
    @Primary
    public KeyResolver userKeyResolver() {

        return exchange -> {

            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");

            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }

            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();

            String ip = (remoteAddress != null)
                    ? remoteAddress.getAddress().getHostAddress()
                    : "unknown";

            return Mono.just("ip:" + ip);
        };
    }
}

