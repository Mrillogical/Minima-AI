package com.minima-AI.ai.config;

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
    public OpenAPI aiServiceOpenAPI() {

        SecurityScheme userIdScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-User-Id");

        // NOTE: /mcp/** isn't a @RestController, won't show here regardless
        return new OpenAPI()
                .info(new Info()
                        .title("minima-AI - AI Service")
                        .description("Spring AI ReAct agent, classification, slug, safety, summary")
                        .version("1.0.0"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id", userIdScheme))
                .addSecurityItem(new SecurityRequirement().addList("X-User-Id"));
    }
}

