package com.minima-AI.auth.common.config;

import com.minima-AI.auth.oauth2.OAuth2SuccessHandler;
import com.minima-AI.auth.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    private final String apiPrefix;

    public SecurityConfig(
            JwtAuthFilter jwtAuthFilter,
            OAuth2SuccessHandler oAuth2SuccessHandler,
            @Value("${api.prefix}") String apiPrefix
    ) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.oAuth2SuccessHandler = oAuth2SuccessHandler;
        this.apiPrefix = apiPrefix;
    }

    // JwtAuthFilter is a @Component, so Boot would also auto-register it
    // globally (/*) on top of the addFilterBefore wiring below - running it
    // twice per request. This turns that automatic registration off.
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration() {

        FilterRegistrationBean<JwtAuthFilter> registration =
                new FilterRegistrationBean<>(jwtAuthFilter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http.csrf(AbstractHttpConfigurer::disable)

                // disabling form login
                .formLogin(AbstractHttpConfigurer::disable)

                // Every request must carry its own JWT - no server-side session state
                // IF_REQUIRED because OAuth 2 saves state after redirecting
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

                // Authorization rules - evaluated top to bottom, first match wins
                .authorizeHttpRequests(auth ->
                        auth.requestMatchers(apiPrefix + "/auth/me").authenticated()
                                .requestMatchers(apiPrefix + "/auth/apikeys/**").authenticated()
                                .requestMatchers(apiPrefix + "/auth/**",
                                        "/actuator/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/swagger-ui/**").permitAll()
                                .anyRequest().authenticated())

                // Wire in JwtAuthFilter - runs BEFORE Spring's default auth filter
                // Order matters - JWT must be validated first so SecurityContext is populated
                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)

                // OAuth config
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(oAuth2SuccessHandler)
                );

        return http.build();
    }

    // BCrypt - password hashing
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}

