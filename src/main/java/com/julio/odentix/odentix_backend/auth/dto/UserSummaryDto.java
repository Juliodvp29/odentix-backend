package com.julio.odentix.odentix_backend.auth.dto;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import java.util.UUID;

/**
 * Resumen de datos de un usuario autenticado (FASE1-06 / FASE1-07).
 */
public class UserSummaryDto {

  private UUID id;
  private UUID tenantId;
  private String email;
  private String fullName;
  private UserRole role;

  public UserSummaryDto() {
  }

  public UserSummaryDto(UUID id, UUID tenantId, String email, String fullName, UserRole role) {
    this.id = id;
    this.tenantId = tenantId;
    this.email = email;
    this.fullName = fullName;
    this.role = role;
  }

  public static UserSummaryDto fromEntity(User user) {
    return new UserSummaryDto(
        user.getId(),
        user.getTenant().getId(),
        user.getEmail(),
        user.getFullName(),
        user.getRole()
    );
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public UserRole getRole() {
    return role;
  }

  public void setRole(UserRole role) {
    this.role = role;
  }
}
