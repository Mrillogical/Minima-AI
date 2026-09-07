package com.minima-AI.gateway.common.fallback;

import com.minima-AI.gateway.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

@RestController
@RequestMapping("/fallback")
@Slf4j
public class FallbackController {

    // no HTTP method specified â€” matches GET/POST/PUT/DELETE alike
    @RequestMapping("/{service}")
    public Mono<ResponseEntity<ErrorResponse>> fallback(
            @PathVariable String service,
            ServerWebExchange exchange
    ) {

        log.warn("Circuit breaker OPEN - {} unavailable for path: {}",
                service, exchange.getRequest().getURI().getPath());

        ErrorResponse error = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "%s is temporarily unavailable. Please try again shortly.".formatted(service),
                exchange.getRequest().getURI().getPath(),
                Instant.now()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error));
    }
}

