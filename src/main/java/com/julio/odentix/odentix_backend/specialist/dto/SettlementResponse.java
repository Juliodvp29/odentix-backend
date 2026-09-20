package com.julio.odentix.odentix_backend.specialist.dto;

import com.julio.odentix.odentix_backend.specialist.entity.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Liquidación de especialista creada (FASE7-02).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class SettlementResponse {

  private UUID id;
  private UUID specialistId;
  private LocalDate periodStart;
  private LocalDate periodEnd;
  private BigDecimal grossProductionCop;
  private BigDecimal feeAmountCop;
  private SettlementStatus status;

  public SettlementResponse() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getSpecialistId() {
    return specialistId;
  }

  public void setSpecialistId(UUID specialistId) {
    this.specialistId = specialistId;
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

  public BigDecimal getGrossProductionCop() {
    return grossProductionCop;
  }

  public void setGrossProductionCop(BigDecimal grossProductionCop) {
    this.grossProductionCop = grossProductionCop;
  }

  public BigDecimal getFeeAmountCop() {
    return feeAmountCop;
  }

  public void setFeeAmountCop(BigDecimal feeAmountCop) {
    this.feeAmountCop = feeAmountCop;
  }

  public SettlementStatus getStatus() {
    return status;
  }

  public void setStatus(SettlementStatus status) {
    this.status = status;
  }
}

