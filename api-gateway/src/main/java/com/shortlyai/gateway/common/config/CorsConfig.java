package com.minima-AI.gateway.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

// Reactive CORS - CorsWebFilter (WebFlux) not CorsConfiguration (Servlet)
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${cors.allowed-origins}")
            List<String> allowedOrigins
    ) {

        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-MCP-Key"));

        // headers
        config.setExposedHeaders(List.of(
                "X-RateLimit-Remaining",        // client knows how many left
                "X-RateLimit-Burst-Capacity",   // client knows max burst
                "Retry-After"                   // already set on 429
        )); // expose to browser JS

        config.setAllowCredentials(true); // allow Authorization header in CORS requests
        config.setMaxAge(3600L);          // browsers cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // apply to all paths

        return new CorsWebFilter(source);
    }
}

