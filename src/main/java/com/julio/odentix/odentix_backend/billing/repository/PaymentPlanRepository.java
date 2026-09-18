package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link PaymentPlan} (FASE6-01).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries, incluida la búsqueda por {@code treatmentPlanId}. No es necesario
 * agregar {@code AND tenant_id = ?} manualmente — está garantizado por la clase
 * base {@code TenantAwareEntity} y el {@code TenantIdentifierResolver}.
 */
public interface PaymentPlanRepository extends JpaRepository<PaymentPlan, UUID> {

  /**
   * Devuelve los planes de pago asociados a un plan de tratamiento específico,
   * filtrados automáticamente por el tenant activo en el contexto de la request.
   *
   * <p>En la práctica un tratamiento debería tener como máximo un plan de pago
   * activo, pero el modelo no lo restringe para permitir renegociaciones futuras.
   */
  List<PaymentPlan> findByTreatmentPlanId(UUID treatmentPlanId);
}
