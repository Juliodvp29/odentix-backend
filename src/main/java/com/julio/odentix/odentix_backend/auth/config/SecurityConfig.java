package com.julio.odentix.odentix_backend.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración base de Spring Security.
 *
 * <p>En esta etapa (FASE0-08 / FASE0-09) permite el acceso anónimo exclusivamente a los
 * endpoints de diagnóstico de Actuator (/actuator/health y /actuator/info) para que
 * plataformas como Render puedan realizar sus health checks sin autenticación.
 *
 * <p>En la Fase 1 esta clase se ampliará con el filtro JWT y las reglas de autorización
 * de negocio.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  // Hash de contraseñas (FASE1-05). BCrypt adapta su costo al hardware;
  // nunca se guarda ni se compara una contraseña en texto plano.
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/v1/auth/**").permitAll()
            .anyRequest().authenticated()
        );
    return http.build();
  }
}
