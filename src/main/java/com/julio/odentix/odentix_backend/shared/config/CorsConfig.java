package com.julio.odentix.odentix_backend.shared.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Orígenes permitidos para peticiones de navegador (CORS).
 *
 * <p>Sin esto, el preflight OPTIONS del navegador no recibe
 * Access-Control-Allow-Origin y bloquea las llamadas desde el frontend
 * aunque el endpoint exista y funcione con curl. La API usa JWT Bearer
 * sin cookies, así que no se permiten credenciales.
 */
@Configuration
public class CorsConfig {

  private final List<String> allowedOrigins;

  public CorsConfig(
      @Value("${odentix.cors.allowed-origins:http://localhost:4200}") String[] allowedOrigins) {
    this.allowedOrigins = Arrays.asList(allowedOrigins);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(allowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));
    configuration.setExposedHeaders(List.of("Authorization", "Retry-After"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", configuration);
    return source;
  }
}
