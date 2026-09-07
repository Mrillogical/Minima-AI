package com.minima-AI.url.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class HeaderAuthFilter extends OncePerRequestFilter {

    // Gateway always passes this header when user is authenticated
    private static final String USER_ID_HEADER = "X-User-Id";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if(request.getRequestURI().startsWith("/actuator")) {

            doFilter(request, response, filterChain);

            return;
        }

        // Read the userId injected by the gateway after JWT validation
        String userId = request.getHeader(USER_ID_HEADER);

        // Null check gates everything - public endpoints won't have this header
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            try {

                // Parse String -> UUID
                UUID parsedUserId = UUID.fromString(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                parsedUserId,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );

                // Store in SecurityContext - .authenticated() check reads this
                SecurityContextHolder
                        .getContext()
                        .setAuthentication(auth);

                log.debug("Authenticated userId: {}", parsedUserId);

            } catch (IllegalArgumentException _) {

                // Header present but not a valid UUID
                log.warn("Invalid X-User-Id header value: {}", userId);
            }

        } else if (userId == null) {

            // Normal for public endpoints
            log.debug("No X-User-Id header on request: {}", request.getRequestURI());
        }

        // Always continue - filter sets context only, never blocks directly
        filterChain.doFilter(request, response);
    }
}

