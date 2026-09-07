package com.minima-AI.analytics.common.security;

import com.minima-AI.analytics.common.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class HeaderAuthFilter extends OncePerRequestFilter {

    private final JsonMapper jsonMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String uri = request.getRequestURI();

        // No need of ID for actuators and Swagger
        if (uri.startsWith("/actuator")
                || uri.startsWith("/swagger")
                || uri.startsWith("/v3")
        ) {

            filterChain.doFilter(request, response);

            return;
        }

        String userId = request.getHeader("X-User-Id");

        // check if userId is missing
        if (userId == null || userId.isBlank()) {

            log.warn("Missing X-User-Id on: {}", request.getRequestURI());

            writeUnauthorized(response, request, "Missing X-User-Id header");

            return;
        }

        try {
            // extract ID
            UUID parsedUserId = UUID.fromString(userId);

            // Standard Spring Security way to represent an authenticated user
            // Principal = UUID userId, credentials = null, role = USER
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            parsedUserId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_USER"))
                    );

            // Store in SecurityContext - .authenticated() check reads this
            SecurityContextHolder.getContext().setAuthentication(auth);

            log.debug("Authenticated userId: {}", parsedUserId);

            // continue filter
            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException e) {

            log.warn("Invalid X-User-Id format on {}: {}", request.getRequestURI(), userId);

            writeUnauthorized(response, request, "Invalid X-User-Id header");

        } finally {

            SecurityContextHolder.clearContext();
        }
    }

    private void writeUnauthorized(
            HttpServletResponse response,
            HttpServletRequest request,
            String message
    ) throws IOException {

        response.setStatus(401);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ErrorResponse error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                message,
                request.getRequestURI()
        );

        response.getWriter().write(jsonMapper.writeValueAsString(error));
    }
}

