package com.julio.odentix.odentix_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición de cierre de sesión con revocación de refresh token (FASE1-IMPROVE).
 */
public class LogoutRequest {

  @NotBlank(message = "El refresh token es obligatorio")
  private String refreshToken;

  public LogoutRequest() {
  }

  public LogoutRequest(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }
}
