package com.minima-AI.ai.mcp.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@Slf4j
@RequiredArgsConstructor
public class McpKeyFilter implements Filter {

    private static final String MCP_KEY_HEADER = "X-MCP-Key";

    private static final String REDIS_MCP_KEY_PREFIX = "mcp:key:";

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain
    ) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        String path = httpRequest.getRequestURI();

        // Only gate /mcp/** - all other paths (agent, classify, safety, etc.) use JWT via gateway
        if (!path.startsWith("/mcp")) {

            chain.doFilter(request, response);

            return;
        }

        String rawKey = httpRequest.getHeader(MCP_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {

            log.warn("MCP request missing X-MCP-Key header, path: {}", path);

            writeUnauthorized((HttpServletResponse) response, "X-MCP-Key header required");

            return;
        }

        // Hash incoming key - lookup Redis by hash, never by raw key
        String hash = sha256Hex(rawKey);

        String userId = stringRedisTemplate
                .opsForValue()
                .get(REDIS_MCP_KEY_PREFIX + hash);

        if (userId == null) {

            // Key not found = never existed, already revoked, or tampered
            log.warn("MCP request with invalid or revoked API key, path: {}", path);

            writeUnauthorized((HttpServletResponse) response, "Invalid or revoked API key");

            return;
        }

        log.debug("MCP request authenticated userId: {}, path: {}", userId, path);

        // Inject userId into ThreadLocal - MCP tools read from here
        McpUserContext.set(userId);

        try {

            chain.doFilter(request, response);

        } finally {

            // ALWAYS clear - virtual thread goes back to pool after this request
            // Without clear(): next MCP request on this thread inherits wrong userId
            McpUserContext.clear();
        }
    }

    // Same SHA-256 logic as auth-service ApiKeyService - must match exactly
    private String sha256Hex(String input) {

        try {

            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {

            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // Write 401 JSON response - request never reaches MCP tool layer
    private void writeUnauthorized(
            HttpServletResponse response,
            String message
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        response.setContentType("application/json");

        response.setCharacterEncoding("UTF-8");

        // Minimal JSON - ai-service GlobalExceptionHandler doesn't intercept Filter-level responses
        response.getWriter().write(
                """
                {
                "status":401,
                "error":"UNAUTHORIZED",
                "message":"%s"
                }
                """.formatted(message).strip()
        );
    }
}

