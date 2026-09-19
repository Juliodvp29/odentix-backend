package com.julio.odentix.odentix_backend.subscription.repository;

import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para el catálogo global de planes (FASE11-01).
 *
 * <p>{@link Plan} no lleva {@code tenant_id}: es el mismo catálogo para
 * todos los tenants, sin filtro multi-tenant.
 */
@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {

  Optional<Plan> findByCode(String code);

  List<Plan> findByActiveTrue();
}
