package com.julio.odentix.odentix_backend.auth.dto;

/**
 * Respuesta a la renovación exitosa de sesión (FASE1-IMPROVE).
 */
public class TokenRefreshResponse {

  private String accessToken;
  private String refreshToken;
  private String tokenType = "Bearer";
  private long expiresInSeconds;

  public TokenRefreshResponse() {
  }

  public TokenRefreshResponse(String accessToken, String refreshToken, long expiresInSeconds) {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
    this.tokenType = "Bearer";
    this.expiresInSeconds = expiresInSeconds;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  public long getExpiresInSeconds() {
    return expiresInSeconds;
  }

  public void setExpiresInSeconds(long expiresInSeconds) {
    this.expiresInSeconds = expiresInSeconds;
  }
}
