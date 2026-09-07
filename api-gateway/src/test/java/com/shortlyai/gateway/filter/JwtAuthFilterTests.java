package com.minima-AI.gateway.filter;

import com.minima-AI.gateway.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthFilterTests {

    @Mock
    JwtUtil jwtUtil;

    @Mock
    JsonMapper jsonMapper;

    @Mock
    GatewayFilterChain chain;

    JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {

        jwtAuthFilter = new JwtAuthFilter(jwtUtil, jsonMapper, "/api/v1");
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    private ServerWebExchange exchangeWithHeaders(String path, org.springframework.http.HttpHeaders headers) {

        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.get(path);
        headers.forEach((name, values) -> values.forEach(v -> builder.header(name, v)));

        return MockServerWebExchange.from(builder.build());
    }

    @Test
    void validJwt_allowsRequestAndInjectsUserIdAndRoleHeaders() {

        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ServerWebExchange exchange =
                exchangeWithHeaders("/api/v1/urls", headers);

        Claims claims = mock(Claims.class);

        when(jwtUtil.validateAccessToken(token))
                .thenReturn(Optional.of(claims));

        when(claims.getSubject())
                .thenReturn(userId.toString());

        when(claims.get("role", String.class))
                .thenReturn("ROLE_FREE");

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain))
                .verifyComplete();

        ArgumentCaptor<ServerWebExchange> captor =
                ArgumentCaptor.forClass(ServerWebExchange.class);

        verify(chain).filter(captor.capture());

        ServerWebExchange forwarded = captor.getValue();

        assertEquals(
                userId.toString(),
                forwarded.getRequest().getHeaders().getFirst("X-User-Id")
        );

        assertEquals(
                "ROLE_FREE",
                forwarded.getRequest().getHeaders().getFirst("X-User-Role")
        );
    }

    @Test
    void missingAuthorizationHeader_rejectsWithUnauthorized() throws Exception {

        ServerWebExchange exchange = exchangeWithHeaders("/api/v1/urls", new org.springframework.http.HttpHeaders());

        when(jsonMapper.writeValueAsBytes(any())).thenReturn("{\"status\":401}".getBytes());

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void invalidJwt_rejectsWithUnauthorized() throws Exception {

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", "Bearer garbage-token");
        ServerWebExchange exchange = exchangeWithHeaders("/api/v1/urls", headers);

        when(jwtUtil.isTokenValid("garbage-token")).thenReturn(false);
        when(jsonMapper.writeValueAsBytes(any())).thenReturn("{\"status\":401}".getBytes());

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any());
    }

    @Test
    void publicPath_bypassesJwtCheckEntirely() {

        // e.g. redirect path - no Authorization header needed
        ServerWebExchange exchange = exchangeWithHeaders("/r/abc123", new org.springframework.http.HttpHeaders());

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(any());
        verify(jwtUtil, never()).isTokenValid(any());
    }

    @Test
    void clientSuppliedXUserIdHeader_isStrippedRegardlessOfPath() {

        // Regression test: a client cannot forge X-User-Id and have it survive
        // through the gateway, even on a public path â€” the filter always
        // rebuilds the request with these headers removed first.
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("X-User-Id", "forged-attacker-controlled-value");
        ServerWebExchange exchange = exchangeWithHeaders("/r/abc123", headers);

        StepVerifier.create(jwtAuthFilter.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(argThat(mutatedExchange ->
                mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id") == null
                        || !"forged-attacker-controlled-value"
                        .equals(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"))
        ));
    }
}

