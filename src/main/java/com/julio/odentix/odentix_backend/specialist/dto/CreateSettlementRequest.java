package com.julio.odentix.odentix_backend.specialist.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Solicitud para generar una liquidación de especialista (FASE7-02).
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
public class CreateSettlementRequest {

  @NotNull(message = "periodStart es obligatorio")
  private LocalDate periodStart;

  @NotNull(message = "periodEnd es obligatorio")
  private LocalDate periodEnd;

  public CreateSettlementRequest() {
  }

  public CreateSettlementRequest(LocalDate periodStart, LocalDate periodEnd) {
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
  }

  public LocalDate getPeriodStart() {
    return periodStart;
  }

  public void setPeriodStart(LocalDate periodStart) {
    this.periodStart = periodStart;
  }

  public LocalDate getPeriodEnd() {
    return periodEnd;
  }

  public void setPeriodEnd(LocalDate periodEnd) {
    this.periodEnd = periodEnd;
  }
}
