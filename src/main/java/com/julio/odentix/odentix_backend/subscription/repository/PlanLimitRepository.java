package com.julio.odentix.odentix_backend.subscription.repository;

import com.julio.odentix.odentix_backend.subscription.entity.PlanLimit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para los límites numéricos por plan (FASE11-01).
 *
 * <p>Catálogo global como {@link com.julio.odentix.odentix_backend.subscription.entity.Plan}:
 * sin {@code tenant_id} ni filtro multi-tenant.
 */
@Repository
public interface PlanLimitRepository extends JpaRepository<PlanLimit, UUID> {

  List<PlanLimit> findByPlanId(UUID planId);

  Optional<PlanLimit> findByPlanIdAndLimitKey(UUID planId, String limitKey);
}
