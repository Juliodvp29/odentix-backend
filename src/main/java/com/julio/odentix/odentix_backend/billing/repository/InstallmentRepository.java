package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

  /**
   * Invoca la función SQL nativa {@code mark_overdue_installments()} (FASE6-03).
   *
   * <p>Actualiza en base de datos todas las cuotas pendientes cuya fecha de
   * vencimiento es anterior a hoy ({@code CURRENT_DATE}) y devuelve el número
   * de cuotas actualizadas a {@code vencida}.
   */
  @Query(value = "SELECT mark_overdue_installments()", nativeQuery = true)
  int markOverdueInstallments();

  /**
   * Obtiene el resumen financiero consolidado de cartera para un tenant (FASE6-04).
   *
   * <p>Agrupa y totaliza montos y conteos de cuotas:
   * <ul>
   *   <li>Total pactado: todas las cuotas de la clínica.</li>
   *   <li>Vencidas: cuotas en estado 'vencida' o pendientes con {@code due_date < CURRENT_DATE}.</li>
   *   <li>Por vencer: cuotas en estado 'pendiente' con {@code due_date >= CURRENT_DATE}.</li>
   *   <li>Al día / Pagadas: cuotas en estado 'pagada'.</li>
   * </ul>
   */
  @Query(value = """
      SELECT
        COALESCE(SUM(amount_cop), 0) AS totalAmountCop,
        COALESCE(SUM(CASE WHEN status = 'vencida' OR (status = 'pendiente' AND due_date < CURRENT_DATE) THEN amount_cop ELSE 0 END), 0) AS overdueAmountCop,
        COALESCE(SUM(CASE WHEN status = 'pendiente' AND due_date >= CURRENT_DATE THEN amount_cop ELSE 0 END), 0) AS upcomingAmountCop,
        COALESCE(SUM(CASE WHEN status = 'pagada' THEN amount_cop ELSE 0 END), 0) AS paidAmountCop,
        COUNT(*) AS totalCount,
        COUNT(CASE WHEN status = 'vencida' OR (status = 'pendiente' AND due_date < CURRENT_DATE) THEN 1 END) AS overdueCount,
        COUNT(CASE WHEN status = 'pendiente' AND due_date >= CURRENT_DATE THEN 1 END) AS upcomingCount,
        COUNT(CASE WHEN status = 'pagada' THEN 1 END) AS paidCount
      FROM installments
      WHERE tenant_id = :tenantId
      """, nativeQuery = true)
  PortfolioSummaryProjection getPortfolioSummary(@Param("tenantId") UUID tenantId);
}
