package com.julio.odentix.odentix_backend.billing.dto;

import java.math.BigDecimal;

/**
 * Resumen consolidado del dashboard de cartera para el tenant activo (FASE6-04).
 *
 * <p>Expone el estado financiero de las cuotas pactadas:
 * <ul>
 *   <li>{@code totalAmountCop}: Cartera total pactada (suma de todas las cuotas).</li>
 *   <li>{@code overdueAmountCop}: Cartera vencida (cuotas vencidas no pagadas).</li>
 *   <li>{@code upcomingAmountCop}: Cartera por vencer (cuotas pendientes con vencimiento futuro).</li>
 *   <li>{@code paidAmountCop}: Cartera al día / pagada (cuotas saldadas).</li>
 *   <li>{@code outstandingAmountCop}: Saldo total por cobrar (vencida + por vencer).</li>
 * </ul>
 *
 * <p>Convención: Sin Lombok (regla §9 de AGENTS.md).
 */
public class PortfolioSummaryResponse {

  private final BigDecimal totalAmountCop;
  private final BigDecimal overdueAmountCop;
  private final BigDecimal upcomingAmountCop;
  private final BigDecimal paidAmountCop;
  private final BigDecimal outstandingAmountCop;
  private final long totalInstallmentsCount;
  private final long overdueInstallmentsCount;
  private final long upcomingInstallmentsCount;
  private final long paidInstallmentsCount;

  public PortfolioSummaryResponse(
      BigDecimal totalAmountCop,
      BigDecimal overdueAmountCop,
      BigDecimal upcomingAmountCop,
      BigDecimal paidAmountCop,
      BigDecimal outstandingAmountCop,
      long totalInstallmentsCount,
      long overdueInstallmentsCount,
      long upcomingInstallmentsCount,
      long paidInstallmentsCount) {
    this.totalAmountCop = totalAmountCop != null ? totalAmountCop : BigDecimal.ZERO;
    this.overdueAmountCop = overdueAmountCop != null ? overdueAmountCop : BigDecimal.ZERO;
    this.upcomingAmountCop = upcomingAmountCop != null ? upcomingAmountCop : BigDecimal.ZERO;
    this.paidAmountCop = paidAmountCop != null ? paidAmountCop : BigDecimal.ZERO;
    this.outstandingAmountCop = outstandingAmountCop != null ? outstandingAmountCop : BigDecimal.ZERO;
    this.totalInstallmentsCount = totalInstallmentsCount;
    this.overdueInstallmentsCount = overdueInstallmentsCount;
    this.upcomingInstallmentsCount = upcomingInstallmentsCount;
    this.paidInstallmentsCount = paidInstallmentsCount;
  }

  public static PortfolioSummaryResponse empty() {
    return new PortfolioSummaryResponse(
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        0L,
        0L,
        0L,
        0L);
  }

  public BigDecimal getTotalAmountCop() {
    return totalAmountCop;
  }

  public BigDecimal getOverdueAmountCop() {
    return overdueAmountCop;
  }

  public BigDecimal getUpcomingAmountCop() {
    return upcomingAmountCop;
  }

  public BigDecimal getPaidAmountCop() {
    return paidAmountCop;
  }

  public BigDecimal getOutstandingAmountCop() {
    return outstandingAmountCop;
  }

  public long getTotalInstallmentsCount() {
    return totalInstallmentsCount;
  }

  public long getOverdueInstallmentsCount() {
    return overdueInstallmentsCount;
  }

  public long getUpcomingInstallmentsCount() {
    return upcomingInstallmentsCount;
  }

  public long getPaidInstallmentsCount() {
    return paidInstallmentsCount;
  }
}
