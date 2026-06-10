package com.scorestv.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger yapilandirmasi.
 * Swagger UI: /swagger-ui.html   |   OpenAPI JSON: /v3/api-docs
 * "Authorize" butonuna access token girilerek korumali endpoint'ler test edilir.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI scorestvOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("scorestv API")
                        .version("v1")
                        .description("scorestv canlı skor ve istatistik servisi API'si"))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
