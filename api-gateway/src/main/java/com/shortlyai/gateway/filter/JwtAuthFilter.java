package com.minima-AI.gateway.filter;

import com.minima-AI.gateway.dto.ErrorResponse;
import com.minima-AI.gateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;

    private final JsonMapper jsonMapper;

    private final String apiPrefix;

    public JwtAuthFilter(
            JwtUtil jwtUtil,
            JsonMapper jsonMapper,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.jwtUtil = jwtUtil;
        this.jsonMapper = jsonMapper;
        this.apiPrefix = apiPrefix;
    }

    @Override
    public int getOrder() {
        return -1; // runs after TraceIdFilter (-2)
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        // Strip any client-supplied trust headers FIRST, on every request,
        // before any public/protected branching. Nothing downstream should
        // ever see an X-User-Id that didn't come from a validated JWT here.
        ServerHttpRequest strippedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove("X-User-Id");
                    headers.remove("X-User-Role");
                })
                .build();

        ServerWebExchange strippedExchange = exchange
                .mutate()
                .request(strippedRequest)
                .build();

        if (isPublicPath(path)) {

            log.debug("Public path, skipping JWT check: {}", path);

            return chain.filter(strippedExchange);
        }

        String authHeader = strippedExchange
                .getRequest()
                .getHeaders()
                .getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {

            log.warn("Missing or invalid Authorization header for path: {}", path);

            return writeError(strippedExchange, HttpStatus.UNAUTHORIZED, "Authorization header required");
        }

        String token = authHeader.substring(7);

        Optional<Claims> validClaims = jwtUtil.validateAccessToken(token);

        if (validClaims.isEmpty()) {

            log.warn("Invalid, expired, or non-access JWT for path: {}", path);

            return writeError(strippedExchange, HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        Claims claims = validClaims.get();
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);

        ServerHttpRequest mutatedRequest = strippedExchange
                .getRequest()
                .mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role)
                .build();

        log.debug("JWT valid, userId: {}, role: {}, forwarding to: {}", userId, role, path);

        return chain.filter(strippedExchange.mutate().request(mutatedRequest).build());
    }

    // Decides if a path requires JWT or not
    private boolean isPublicPath(String path) {

        // All auth endpoints are public
        if (path.startsWith(apiPrefix + "/auth/")) return true;

        // OAuth2 callback - Spring Security handles these internally in auth-service
        if (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/")) return true;

        if (path.equals("/mcp") || path.startsWith("/mcp/")) return true;

        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")) return true;

        // Short URL redirect endpoint
        return path.startsWith("/r/");
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            HttpStatus status,
            String message
    ) {

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        try {

            // Serialize ErrorResponse -> JSON bytes -> DataBuffer
            byte[] bytes = jsonMapper.writeValueAsBytes(error);

            DataBuffer buffer = response.bufferFactory().wrap(bytes);

            return response.writeWith(Mono.just(buffer));

        } catch (Exception e) {

            log.error("Failed to serialize error response", e);

            return response.setComplete(); // send empty response rather than hang
        }
    }
}

