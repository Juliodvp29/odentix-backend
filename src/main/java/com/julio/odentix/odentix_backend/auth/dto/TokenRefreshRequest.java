package com.julio.odentix.odentix_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Petición de renovación de tokens de sesión (FASE1-IMPROVE).
 */
public class TokenRefreshRequest {

  @NotBlank(message = "El refresh token es obligatorio")
  private String refreshToken;

  public TokenRefreshRequest() {
  }

  public TokenRefreshRequest(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }
}
