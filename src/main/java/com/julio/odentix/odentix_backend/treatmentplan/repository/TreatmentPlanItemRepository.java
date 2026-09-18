package com.julio.odentix.odentix_backend.treatmentplan.repository;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link TreatmentPlanItem} (FASE4-01).
 */
@Repository
public interface TreatmentPlanItemRepository extends JpaRepository<TreatmentPlanItem, UUID> {

  /**
   * Busca un ítem individual por ID y tenant.
   */
  Optional<TreatmentPlanItem> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Lista todos los ítems asociados a un plan de tratamiento dentro del tenant activo.
   */
  List<TreatmentPlanItem> findByTreatmentPlanIdAndTenantId(UUID treatmentPlanId, UUID tenantId);
}
