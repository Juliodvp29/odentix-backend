package com.julio.odentix.odentix_backend.billing.repository;

import java.math.BigDecimal;

/**
 * Proyección de Spring Data para mapear los resultados agregados del dashboard de cartera (FASE6-04).
 */
public interface PortfolioSummaryProjection {

  BigDecimal getTotalAmountCop();

  BigDecimal getOverdueAmountCop();

  BigDecimal getUpcomingAmountCop();

  BigDecimal getPaidAmountCop();

  Long getTotalCount();

  Long getOverdueCount();

  Long getUpcomingCount();

  Long getPaidCount();
}
