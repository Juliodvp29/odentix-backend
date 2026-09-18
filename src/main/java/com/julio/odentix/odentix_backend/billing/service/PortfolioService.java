package com.julio.odentix.odentix_backend.billing.service;

import com.julio.odentix.odentix_backend.billing.dto.PortfolioSummaryResponse;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PortfolioSummaryProjection;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para el dashboard y métricas de cartera (FASE6-04).
 *
 * <p>Consolida los totales e indicadores de cartera del tenant activo:
 * montos totales pactados, vencidos, por vencer, recaudados (al día), y saldo pendiente.
 */
@Service
public class PortfolioService {

  private final InstallmentRepository installmentRepository;

  public PortfolioService(InstallmentRepository installmentRepository) {
    this.installmentRepository = installmentRepository;
  }

  /**
   * Obtiene el resumen financiero consolidado de cartera para la clínica activa.
   *
   * @return DTO con los totales y conteos desglosados de cartera.
   */
  @Transactional(readOnly = true)
  public PortfolioSummaryResponse getSummary() {
    UUID tenantId = TenantContext.getRequiredTenantId();
    PortfolioSummaryProjection projection = installmentRepository.getPortfolioSummary(tenantId);

    if (projection == null) {
      return PortfolioSummaryResponse.empty();
    }

    BigDecimal totalAmount = projection.getTotalAmountCop() != null
        ? projection.getTotalAmountCop()
        : BigDecimal.ZERO;
    BigDecimal overdueAmount = projection.getOverdueAmountCop() != null
        ? projection.getOverdueAmountCop()
        : BigDecimal.ZERO;
    BigDecimal upcomingAmount = projection.getUpcomingAmountCop() != null
        ? projection.getUpcomingAmountCop()
        : BigDecimal.ZERO;
    BigDecimal paidAmount = projection.getPaidAmountCop() != null
        ? projection.getPaidAmountCop()
        : BigDecimal.ZERO;
    BigDecimal outstandingAmount = overdueAmount.add(upcomingAmount);

    long totalCount = projection.getTotalCount() != null ? projection.getTotalCount() : 0L;
    long overdueCount = projection.getOverdueCount() != null ? projection.getOverdueCount() : 0L;
    long upcomingCount = projection.getUpcomingCount() != null ? projection.getUpcomingCount() : 0L;
    long paidCount = projection.getPaidCount() != null ? projection.getPaidCount() : 0L;

    return new PortfolioSummaryResponse(
        totalAmount,
        overdueAmount,
        upcomingAmount,
        paidAmount,
        outstandingAmount,
        totalCount,
        overdueCount,
        upcomingCount,
        paidCount);
  }
}
