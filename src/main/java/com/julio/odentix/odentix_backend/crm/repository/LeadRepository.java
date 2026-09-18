package com.julio.odentix.odentix_backend.crm.repository;

import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para prospectos comerciales (FASE5-01).
 *
 * <p>Aislamiento multi-tenant garantizado automáticamente por {@code @TenantId}
 * en {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity}.
 * Métodos con {@code tenantId} explícito se proporcionan por defensa en profundidad
 * (regla §5 de AGENTS.md).
 */
@Repository
public interface LeadRepository extends JpaRepository<Lead, UUID> {

  Optional<Lead> findByIdAndTenantId(UUID id, UUID tenantId);

  Page<Lead> findByStatus(LeadStatus status, Pageable pageable);

  Page<Lead> findByStatusAndTenantId(LeadStatus status, UUID tenantId, Pageable pageable);

  Page<Lead> findByAssignedToId(UUID userId, Pageable pageable);

  Page<Lead> findByAssignedToIdAndTenantId(UUID userId, UUID tenantId, Pageable pageable);

  List<Lead> findByTenantIdAndStatus(UUID tenantId, LeadStatus status);

  long countByTenantIdAndStatus(UUID tenantId, LeadStatus status);

  long countByTenantId(UUID tenantId);
}
