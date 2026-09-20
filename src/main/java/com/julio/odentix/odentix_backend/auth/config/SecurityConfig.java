package com.julio.odentix.odentix_backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración de Spring Security con autenticación JWT stateless (FASE1-07)
 * y autorización basada en métodos con @PreAuthorize (FASE1-11).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler;

  public SecurityConfig(
      JwtAuthenticationFilter jwtAuthenticationFilter,
      JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
      JwtAccessDeniedHandler jwtAccessDeniedHandler) {
    this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
    this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
  }

  // Hash de contraseñas (FASE1-05). BCrypt adapta su costo al hardware;
  // nunca se guarda ni se compara una contraseña en texto plano.
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // FASE12-01 (revisión CSRF/XSS explícita): API REST pura con JWT Bearer
        // stateless — sin sesiones, sin cookies, sin HTML renderizado. CSRF solo
        // aplica a sesiones basadas en cookies, así que deshabilitarlo aquí es
        // correcto (no hay token de sesión que un sitio malicioso pueda hacer
        // usar al navegador). XSS reflejado tampoco aplica: todo error se
        // serializa como JSON, nunca como HTML (ver SecurityHeadersIntegrationTest).
        .csrf(AbstractHttpConfigurer::disable)
        // Cabeceras explícitas anti-clickjacking y anti-MIME-sniffing. La API
        // nunca se embebe en iframes ni sirve contenido ejecutable.
        .headers(headers -> headers
            .contentTypeOptions(contentType -> {
            })
            .frameOptions(frame -> frame.deny()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(ex -> ex
            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
            .accessDeniedHandler(jwtAccessDeniedHandler)
        )
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
            // Webhook de Bold (FASE11-04): lo llama Bold sin JWT; se autentica
            // por firma HMAC (x-bold-signature) en el propio endpoint.
            .requestMatchers("/api/v1/billing/webhooks/**").permitAll()
            // Documentación interactiva (deshabilitada en prod vía application-prod.yml).
            .requestMatchers("/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
