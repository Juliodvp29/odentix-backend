package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio JPA para {@link Installment} (FASE6-01).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) aplica en todas
 * las queries. Las cuotas se devuelven ordenadas por número para mantener
 * la lectura del plan coherente con su definición.
 */
public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

  /**
   * Devuelve todas las cuotas de un plan de pago, ordenadas por número.
   * Filtradas automáticamente por el tenant activo.
   */
  List<Installment> findByPaymentPlanIdOrderByInstallmentNumberAsc(UUID paymentPlanId);

  /**
   * Devuelve las cuotas de un tenant en un estado específico.
   * Insumo del dashboard de cartera (FASE6-04) y del job de vencidas (FASE6-03).
   */
  List<Installment> findByStatus(InstallmentStatus status);
}
