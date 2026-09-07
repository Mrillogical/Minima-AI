package com.minima-AI.analytics.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI analyticsServiceOpenAPI() {

        SecurityScheme userIdScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-User-Id");

        return new OpenAPI()
                .info(new Info()
                        .title("minima-AI - Analytics Service")
                        .description("Click tracking, hourly rollups, Bloom filter dedup")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", userIdScheme))
                .addSecurityItem(new SecurityRequirement().addList("X-User-Id"));
    }
}

