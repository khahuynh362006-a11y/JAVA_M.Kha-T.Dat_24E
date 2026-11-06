package com.cineverse.shared;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;

import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI apiDocs() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("CineVerse API")
                                .version("1.0.0")
                                .description(
                                        "Java capstone REST API. Login returns a short-lived access"
                                                + " JWT; refresh uses an HttpOnly cookie with exact"
                                                + " Origin validation. SIMULATION payments are"
                                                + " development-only."))
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
