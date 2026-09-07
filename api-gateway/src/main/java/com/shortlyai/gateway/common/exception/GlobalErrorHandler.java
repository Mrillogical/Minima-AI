package com.minima-AI.gateway.common.exception;

import com.minima-AI.gateway.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

// WebExceptionHandler - reactive equivalent of @ControllerAdvice
// @Order(-2) - runs before Spring Boot's default error handler
// Catches: routing errors (no route found), downstream service errors, unexpected exceptions
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements WebExceptionHandler {

    private final JsonMapper jsonMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {

        log.error("Gateway error on path {}: {}", exchange.getRequest().getURI().getPath(), ex.getMessage());

        HttpStatus status = resolveStatus(ex);
        String message = resolveMessage(ex);

        ErrorResponse error = new ErrorResponse(
                status.value(),
                message,
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {

            byte[] bytes = jsonMapper.writeValueAsBytes(error);

            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);

            return exchange.getResponse().writeWith(Mono.just(buffer));

        } catch (Exception e) {

            log.error("Failed to write error response", e);

            return exchange.getResponse().setComplete();
        }
    }

    // Map exception type to HTTP status
    private HttpStatus resolveStatus(Throwable ex) {

        if (ex instanceof ResponseStatusException rse) {

            return HttpStatus.valueOf(rse.getStatusCode().value());
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    // Human-readable message â€” never expose internal stacktrace
    private String resolveMessage(Throwable ex) {

        if (ex instanceof ResponseStatusException rse && rse.getReason() != null) {
            return rse.getReason();
        }

        // 404 from gateway = no route matched â€” message is meaningful
        if (ex instanceof ResponseStatusException rse &&
                rse.getStatusCode() == HttpStatus.NOT_FOUND) {

            return "Route not found";
        }

        return "An unexpected error occurred";
    }
}

