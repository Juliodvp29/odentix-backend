package com.julio.odentix.odentix_backend.auth.dto;

/**
 * Respuesta exitosa de autenticación con JWT (FASE1-06).
 */
public class LoginResponse {

  private String accessToken;
  private String tokenType = "Bearer";
  private long expiresInSeconds;
  private UserSummaryDto user;

  public LoginResponse() {
  }

  public LoginResponse(String accessToken, long expiresInSeconds, UserSummaryDto user) {
    this.accessToken = accessToken;
    this.tokenType = "Bearer";
    this.expiresInSeconds = expiresInSeconds;
    this.user = user;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
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

  public UserSummaryDto getUser() {
    return user;
  }

  public void setUser(UserSummaryDto user) {
    this.user = user;
  }
}
