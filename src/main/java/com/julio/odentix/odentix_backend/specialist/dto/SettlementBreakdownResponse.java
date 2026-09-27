package com.julio.odentix.odentix_backend.specialist.dto;

import com.julio.odentix.odentix_backend.specialist.entity.SettlementStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Desglose de una liquidación: las facturas que componen la producción
 * bruta del periodo con los totales de la liquidación.
 *
 * <p>Las líneas se calculan al momento de la consulta con el mismo criterio
 * del cálculo original (facturas emitidas en el periodo vinculadas a
 * tratamientos del profesional, excluyendo anuladas): si después de generar
 * la liquidación se emiten o anulan facturas del periodo, el desglose
 * refleja el estado vigente, no una foto del momento de la generación.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class SettlementBreakdownResponse {

  private UUID settlementId;
  private UUID specialistId;
  private LocalDate periodStart;
  private LocalDate periodEnd;
  private BigDecimal grossProductionCop;
  private BigDecimal feeAmountCop;
  private SettlementStatus status;
  private int invoiceCount;
  private List<SettlementBreakdownLineResponse> invoices;

  public SettlementBreakdownResponse() {
  }

  public UUID getSettlementId() {
    return settlementId;
  }

  public void setSettlementId(UUID settlementId) {
    this.settlementId = settlementId;
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

  public int getInvoiceCount() {
    return invoiceCount;
  }

  public void setInvoiceCount(int invoiceCount) {
    this.invoiceCount = invoiceCount;
  }

  public List<SettlementBreakdownLineResponse> getInvoices() {
    return invoices;
  }

  public void setInvoices(List<SettlementBreakdownLineResponse> invoices) {
    this.invoices = invoices;
  }
}
