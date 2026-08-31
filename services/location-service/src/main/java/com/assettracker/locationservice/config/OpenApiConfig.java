package com.assettracker.locationservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI / Swagger UI metadata. UI at {@code /swagger-ui.html}, spec at {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI apiInfo() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Location Service API")
                .version("v1")
                .description("Desks, rooms and sites; QR-tag lookup."));
  }
}
