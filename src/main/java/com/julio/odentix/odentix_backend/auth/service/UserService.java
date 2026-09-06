package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creación interna de usuarios (FASE1-05).
 *
 * <p>No se expone como registro público: los usuarios los crea el propietario
 * o un proceso interno. La contraseña siempre se guarda con hash BCrypt,
 * nunca en texto plano.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Service
public class UserService {

  private final UserRepository userRepository;
  private final TenantRepository tenantRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      TenantRepository tenantRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.tenantRepository = tenantRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Crea un usuario activo en el tenant indicado, hasheando la contraseña.
   *
   * @throws IllegalArgumentException si el tenant no existe o faltan datos obligatorios
   * @throws IllegalStateException si ya existe ese email en el mismo tenant
   */
  @Transactional
  public User createUser(
      UUID tenantId, String email, String rawPassword, String fullName, UserRole role) {
    if (tenantId == null || email == null || email.isBlank()
        || rawPassword == null || rawPassword.isBlank()
        || fullName == null || fullName.isBlank() || role == null) {
      throw new IllegalArgumentException("tenant, email, password, nombre y rol son obligatorios");
    }
    Tenant tenant = tenantRepository.findById(tenantId)
        .orElseThrow(() -> new IllegalArgumentException("Tenant no existe: " + tenantId));
    if (userRepository.existsByTenantIdAndEmail(tenantId, email)) {
      throw new IllegalStateException("Ya existe un usuario con ese email en el tenant");
    }
    User user = new User(tenant, email, passwordEncoder.encode(rawPassword), fullName, role);
    return userRepository.save(user);
  }
}
