package com.julio.odentix.odentix_backend.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación interactiva del API (Swagger UI en /swagger-ui.html,
 * JSON en /v3/api-docs).
 *
 * <p>El esquema `bearerAuth` conecta el botón "Authorize" de la UI con
 * nuestro JWT propio (FASE1-06): el login devuelve el token y la UI lo
 * envía como `Authorization: Bearer ...` en cada prueba.
 */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI odentixOpenAPI() {
    return new OpenAPI()
        .info(new Info()
            .title("Odentix API")
            .version("v1")
            .description("Backend SaaS multi-tenant para clínicas odontológicas. "
                + "Todos los endpoints (salvo login y health) exigen JWT de un usuario "
                + "autenticado; los datos siempre se aíslan por tenant."))
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components().addSecuritySchemes("bearerAuth",
            new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")));
  }
}
