package com.julio.odentix.odentix_backend.subscription.repository;

import com.julio.odentix.odentix_backend.subscription.entity.PlanFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para los feature flags por plan (FASE11-01).
 *
 * <p>Catálogo global como {@link com.julio.odentix.odentix_backend.subscription.entity.Plan}:
 * sin {@code tenant_id} ni filtro multi-tenant.
 */
@Repository
public interface PlanFeatureRepository extends JpaRepository<PlanFeature, UUID> {

  List<PlanFeature> findByPlanId(UUID planId);

  Optional<PlanFeature> findByPlanIdAndFeatureKey(UUID planId, String featureKey);
}
