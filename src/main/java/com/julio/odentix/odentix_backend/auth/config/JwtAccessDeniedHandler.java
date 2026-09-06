package com.julio.odentix.odentix_backend.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Manejador de excepciones para accesos denegados por falta de privilegios o rol insuficiente (FASE1-11).
 * Retorna HTTP 403 Forbidden en formato JSON uniforme.
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {

    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.getWriter().write("{\"error\":\"Acceso denegado\",\"message\":\"No tienes permisos suficientes para acceder a este recurso\"}");
  }
}
