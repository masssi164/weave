package com.massimotter.weave.backend.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(
        name = "bearer-jwt",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    OpenAPI weaveOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addResponses("UnauthorizedError", new ApiResponse()
                                .description("Missing or invalid bearer token.")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse")))))
                        .addResponses("ForbiddenError", new ApiResponse()
                                .description("Bearer token is missing the weave:workspace scope.")
                                .content(new Content().addMediaType("application/json",
                                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))))))
                .info(new Info()
                        .title("Weave Backend API")
                        .version("v1")
                        .description("JWT-protected product API for workspace capabilities and orchestration."));
    }
}
