package com.managementcontrol.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Опис REST API для Swagger UI (/swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI managementControlOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Management Control API")
                        .version("0.1.0")
                        .description("Сервер системи моніторингу робочих місць: прийом телеметрії від агента "
                                + "та керування пристроями.")
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT-токен адміністратора, отриманий через POST /api/v1/auth/login"))
                        .addSecuritySchemes("deviceToken",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("mc_dev_...")
                                        .description("Device-токен агента, отриманий через POST /api/v1/agent/enroll")));
    }
}