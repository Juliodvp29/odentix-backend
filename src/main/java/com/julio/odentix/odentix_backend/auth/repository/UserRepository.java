package com.julio.odentix.odentix_backend.auth.repository;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad User (FASE1-02 y FASE1-03).
 *
 * <p>Todas las consultas filtran obligatoriamente por tenantId (defensa en profundidad por tenant).
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByTenantIdAndEmail(UUID tenantId, String email);

  List<User> findByEmail(String email);

  boolean existsByTenantIdAndEmail(UUID tenantId, String email);

  Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

  List<User> findAllByTenantId(UUID tenantId);

  /**
   * Conteo de usuarios activos (insumo del límite `max_users` de FASE11-03).
   */
  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  List<User> findAllByTenantIdAndRole(UUID tenantId, UserRole role);
}

