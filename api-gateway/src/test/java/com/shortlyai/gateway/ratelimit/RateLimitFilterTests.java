package com.minima-AI.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitFilterTests {

    @Mock
    RedisRateLimiter redisRateLimiter;

    @Mock
    KeyResolver userKeyResolver;

    @Mock
    JsonMapper jsonMapper;

    @Mock
    GatewayFilterChain chain;

    RateLimitFilter rateLimitFilter;

    @BeforeEach
    void setUp() {
        rateLimitFilter = new RateLimitFilter(redisRateLimiter, userKeyResolver, jsonMapper, "/api/v1");
        when(chain.filter(any())).thenReturn(Mono.empty());
        when(userKeyResolver.resolve(any())).thenReturn(Mono.just("user:abc"));
    }

    private ServerWebExchange exchangeFor(String path) {
        ServerHttpRequest request = MockServerHttpRequest.get(path).build();
        return MockServerWebExchange.from((MockServerHttpRequest) request);
    }

    @Test
    void allowedRequest_passesToChain() {

        ServerWebExchange exchange = exchangeFor("/api/v1/urls");

        RateLimiter.Response allowedResponse = new RateLimiter.Response(true, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("url"), anyString())).thenReturn(Mono.just(allowedResponse));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getStatusCode()).isNull(); // untouched â€” chain owns the response
    }

    @Test
    void deniedRequest_returns429WithRetryAfterHeader() throws Exception {

        ServerWebExchange exchange = exchangeFor("/api/v1/urls");

        RateLimiter.Response deniedResponse = new RateLimiter.Response(false, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("url"), anyString())).thenReturn(Mono.just(deniedResponse));
        when(jsonMapper.writeValueAsBytes(any())).thenReturn("{\"status\":429}".getBytes());

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("1");
        verify(chain, never()).filter(exchange);
    }

    @Test
    void redisThrowsError_failsOpenAndAllowsRequestThrough() {

        ServerWebExchange exchange = exchangeFor("/api/v1/urls");

        when(redisRateLimiter.isAllowed(eq("url"), anyString()))
                .thenReturn(Mono.error(new RuntimeException("Redis connection refused")));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain))
                .verifyComplete();

        // Fail-open is the intended design â€” Redis outage must never block all traffic
        verify(chain).filter(exchange);
    }

    @Test
    void authPath_resolvesToAuthRouteBucket() {

        ServerWebExchange exchange = exchangeFor("/api/v1/auth/login");

        RateLimiter.Response allowed = new RateLimiter.Response(true, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("auth"), anyString())).thenReturn(Mono.just(allowed));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain)).verifyComplete();

        verify(redisRateLimiter).isAllowed(eq("auth"), anyString());
    }

    @Test
    void redirectPath_resolvesToUrlRouteBucket_notItsOwnBucket() {

        ServerWebExchange exchange = exchangeFor("/r/abc123");

        RateLimiter.Response allowed = new RateLimiter.Response(true, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("url"), anyString())).thenReturn(Mono.just(allowed));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain)).verifyComplete();

        verify(redisRateLimiter).isAllowed(eq("url"), anyString());
    }

    @Test
    void unrecognizedPath_fallsBackToDefaultBucket() {

        ServerWebExchange exchange = exchangeFor("/api/v1/something-unmapped");

        RateLimiter.Response allowed = new RateLimiter.Response(true, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("default"), anyString())).thenReturn(Mono.just(allowed));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain)).verifyComplete();

        verify(redisRateLimiter).isAllowed(eq("default"), anyString());
    }

    @Test
    void bucketKey_isPrefixedWithRouteId_toPreventCrossServiceBleed() {

        ServerWebExchange exchange = exchangeFor("/api/v1/urls");

        RateLimiter.Response allowed = new RateLimiter.Response(true, java.util.Map.of());
        when(redisRateLimiter.isAllowed(eq("url"), eq("url:user:abc"))).thenReturn(Mono.just(allowed));

        StepVerifier.create(rateLimitFilter.filter(exchange, chain)).verifyComplete();

        verify(redisRateLimiter).isAllowed("url", "url:user:abc");
    }
}

