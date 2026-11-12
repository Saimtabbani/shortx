package com.shortx.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Everything here is documentation metadata, not behavior — it doesn't
 * change what any endpoint does, only how springdoc describes it at
 * {@code /swagger-ui.html} and {@code /api-docs}. The one functionally
 * important piece is the {@code bearerAuth} security scheme: without it,
 * Swagger UI has no "Authorize" button and no way to attach a token to a
 * try-it-out request, making the protected endpoints undocumented-as-
 * protected and untestable from the UI even though SecurityConfig
 * correctly enforces them.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI shortxOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("ShortX API")
                        .description("""
                                Distributed URL shortener: create short links with optional custom \
                                aliases, expiration, and password protection; track click analytics \
                                (geo, device, browser); JWT-authenticated account and link management.

                                To try authenticated endpoints below: call POST /api/v1/auth/register \
                                or /login, copy the returned accessToken, then click Authorize and \
                                paste it (just the token - Swagger adds the "Bearer " prefix automatically).""")
                        .version("v1")
                        .contact(new Contact()
                                .name("Saim Tabbani")
                                .url("https://github.com/Saimtabbani/shortx"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("/").description("Current environment")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token issued by /api/v1/auth/login, /register, or /refresh.")));
    }
}
