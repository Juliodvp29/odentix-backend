package com.julio.odentix.odentix_backend.tenant.repository;

import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para la entidad Tenant (FASE1-01).
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

  Optional<Tenant> findByName(String name);

  boolean existsByName(String name);
}
