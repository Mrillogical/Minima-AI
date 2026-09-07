package com.minima-AI.ai.common.security;

import com.minima-AI.ai.mcp.auth.McpKeyFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final HeaderAuthFilter headerAuthFilter;

    private final McpKeyFilter mcpKeyFilter;

    // Both filters are @Component, so Boot would also auto-register each of
    // them globally (/*) on top of the addFilterBefore wiring below - running
    // each one twice per request. These turn that automatic registration off;
    @Bean
    public FilterRegistrationBean<HeaderAuthFilter> headerAuthFilterRegistration() {

        FilterRegistrationBean<HeaderAuthFilter> registration =
                new FilterRegistrationBean<>(headerAuthFilter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public FilterRegistrationBean<McpKeyFilter> mcpKeyFilterRegistration() {

        FilterRegistrationBean<McpKeyFilter> registration =
                new FilterRegistrationBean<>(mcpKeyFilter);

        registration.setEnabled(false);

        return registration;
    }

    // MCP security
    // Auth is fully delegated to McpKeyFilter (X-MCP-Key -> Redis lookup), not
    // Spring's AuthenticationManager
    @Bean
    @Order(1)
    public SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/mcp/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(mcpKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

