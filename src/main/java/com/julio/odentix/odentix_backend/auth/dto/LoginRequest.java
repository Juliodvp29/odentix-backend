package com.julio.odentix.odentix_backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * Petición de autenticación de usuario (FASE1-06).
 */
public class LoginRequest {

  @NotBlank(message = "El email no puede estar vacío")
  @Email(message = "El formato de email es inválido")
  private String email;

  @NotBlank(message = "La contraseña no puede estar vacía")
  private String password;

  // Opcional: si un mismo email existiese en múltiples tenants
  private UUID tenantId;

  public LoginRequest() {
  }

  public LoginRequest(String email, String password) {
    this.email = email;
    this.password = password;
  }

  public LoginRequest(String email, String password, UUID tenantId) {
    this.email = email;
    this.password = password;
    this.tenantId = tenantId;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }
}
