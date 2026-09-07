package com.minima-AI.url.common.config;

import com.minima-AI.url.common.security.HeaderAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthFilter headerAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // Disable CSRF - stateless REST API, no browser sessions, not needed
                .csrf(AbstractHttpConfigurer::disable)

                // Disable default form login page - this is a REST service, not a web app
                .formLogin(AbstractHttpConfigurer::disable)

                // Disable HTTP Basic auth popup - JWT/header only
                .httpBasic(AbstractHttpConfigurer::disable)

                // Tell Spring Security: never create/use HttpSession
                // Every request must carry its own identity (X-User-Id header from gateway)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth

                        // Redirect endpoint is public - anyone can follow a short link
                        .requestMatchers(HttpMethod.GET, "/r/**",
                                "/actuator/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/actuator/health").permitAll()

                        // Everything else requires a valid X-User-Id header
                        // The gateway already validated the JWT - we just trust the header
                        .anyRequest().authenticated()
                );

        // No adding any JWT filter here.
        // url-service never sees the JWT token.
        // The gateway extracts userId and passes it as X-User-Id header.
        // A custom filter (HeaderAuthFilter) reads that header - wired separately.
        http.addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

