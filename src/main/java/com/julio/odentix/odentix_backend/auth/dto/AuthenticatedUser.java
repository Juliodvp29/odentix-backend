package com.julio.odentix.odentix_backend.auth.dto;

import java.io.Serializable;
import java.util.UUID;

/**
 * Representa al usuario autenticado en el contexto de seguridad de Spring (FASE1-07).
 */
public class AuthenticatedUser implements Serializable {

  private final UUID userId;
  private final UUID tenantId;
  private final String email;
  private final String role;

  public AuthenticatedUser(UUID userId, UUID tenantId, String email, String role) {
    this.userId = userId;
    this.tenantId = tenantId;
    this.email = email;
    this.role = role;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public String getEmail() {
    return email;
  }

  public String getRole() {
    return role;
  }

  @Override
  public String toString() {
    return "AuthenticatedUser{" +
        "userId=" + userId +
        ", tenantId=" + tenantId +
        ", email='" + email + '\'' +
        ", role='" + role + '\'' +
        '}';
  }
}
