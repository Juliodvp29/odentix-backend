package com.julio.odentix.odentix_backend.auth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Punto de entrada para peticiones no autenticadas que intentan acceder a recursos protegidos.
 * Retorna HTTP 401 en formato JSON consistente e incluye la cabecera estándar WWW-Authenticate
 * según RFC 6750 distinguiendo tokens expirados, inválidos, ausentes o usuarios inactivos.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    String errorCode = (String) request.getAttribute("jwt_auth_error_code");
    String errorMessage = (String) request.getAttribute("jwt_auth_error_message");

    if (errorCode == null) {
      errorCode = "token_missing";
      errorMessage = "Acceso denegado: se requiere un token JWT válido";
    }

    String wwwAuthenticate;
    if ("token_expired".equals(errorCode)) {
      wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"The access token expired\"";
    } else if ("token_invalid".equals(errorCode)) {
      wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"The access token is invalid or tampered\"";
    } else if ("user_inactive".equals(errorCode)) {
      wwwAuthenticate = "Bearer error=\"invalid_token\", error_description=\"User is inactive or not found\"";
    } else {
      wwwAuthenticate = "Bearer error=\"unauthorized\"";
    }

    response.setHeader("WWW-Authenticate", wwwAuthenticate);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    String json = String.format(
        "{\"error\":\"No autorizado\",\"code\":\"%s\",\"message\":\"%s\"}",
        errorCode,
        errorMessage.replace("\"", "\\\"")
    );
    response.getWriter().write(json);
  }
}
