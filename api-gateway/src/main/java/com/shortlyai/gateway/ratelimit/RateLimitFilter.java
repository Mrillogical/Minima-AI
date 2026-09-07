package com.minima-AI.gateway.ratelimit;

import com.minima-AI.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

// Custom GlobalFilter - replaces RequestRateLimiter default-filter in yaml
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter redisRateLimiter;

    private final KeyResolver userKeyResolver;

    private final JsonMapper jsonMapper;

    private final String apiPrefix;

    public RateLimitFilter(
            RedisRateLimiter redisRateLimiter,
            KeyResolver userKeyResolver,
            JsonMapper jsonMapper,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.redisRateLimiter = redisRateLimiter;
        this.userKeyResolver = userKeyResolver;
        this.jsonMapper = jsonMapper;
        this.apiPrefix = apiPrefix;
    }

    @Override
    public int getOrder() {
        return 1; // after JWT (-1), before routing
    }

    @Override
    public Mono<Void> filter(
            ServerWebExchange exchange,
            GatewayFilterChain chain
    ) {

        String path = exchange.getRequest().getURI().getPath();
        String routeId = resolveRouteId(path); // which service bucket config to use

        return userKeyResolver.resolve(exchange)
                .flatMap(userKey -> {

                    // CRITICAL: prefix routeId to userKey
                    // Without this: auth and url share same bucket for same user
                    // With this: "auth:user:uuid" â‰  "url:user:uuid" - separate buckets
                    String bucketKey = routeId + ":" + userKey;  // "auth:user:uuid", "url:user:uuid"

                    log.debug("Rate check for route: {}, bucket: {}", routeId, bucketKey);

                    // isAllowed(routeId, bucketKey):
                    //   routeId   -> picks Config (replenishRate, burstCapacity) from the map
                    //   bucketKey -> Redis key suffix for token/timestamp storage
                    return redisRateLimiter.isAllowed(routeId, bucketKey);
                })
                .flatMap(response -> {

                    // Forward all rate limit metadata headers to client response
                    response.getHeaders().forEach((headerName, headerValue) ->
                            exchange.getResponse().getHeaders().set(headerName, headerValue)
                    );

                    if (response.isAllowed()) {
                        return chain.filter(exchange);
                    }

                    log.warn("Rate limit hit for route: {}, path: {}", routeId, path);

                    return writeError(exchange,
                            HttpStatus.TOO_MANY_REQUESTS,
                            "Rate limit exceeded for " + routeId
                    );
                })
                .onErrorResume(ex -> {

                    // Fail open - Redis down should not block all traffic
                    log.error("Rate limiter error on route: {}, failing open: {}", routeId, ex.getMessage());

                    return chain.filter(exchange);
                });
    }

    // Maps request path -> routeId -> which rate config to apply
    // routeId must match keys in rate-limit.routes yaml (and in the bean's config map)
    private String resolveRouteId(String path) {

        if (path.startsWith(apiPrefix + "/auth")) return "auth";

        if (path.startsWith(apiPrefix + "/urls")) return "url";

        if (path.startsWith(apiPrefix + "/analytics")) return "analytics";

        if (path.startsWith(apiPrefix + "/ai")) return "ai";

        if (path.startsWith("/r/") || path.startsWith(apiPrefix + "/r")) return "url";

        if (path.equals("/mcp") || path.startsWith("/mcp/")) return "mcp";

        return "default"; // catch-all - uses constructor fallback config
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {

        ServerHttpResponse response = exchange.getResponse();

        response.setStatusCode(status);

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        response.getHeaders().add("Retry-After", "1"); // tell client: retry after 1 sec

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        try {

            byte[] bytes = jsonMapper.writeValueAsBytes(error);

            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));

        } catch (Exception e) {

            log.error("Failed to serialize rate limit error", e);

            return response.setComplete();
        }
    }
}

