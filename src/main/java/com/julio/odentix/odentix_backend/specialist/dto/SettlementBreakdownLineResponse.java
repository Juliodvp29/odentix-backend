package com.julio.odentix.odentix_backend.specialist.dto;

import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Línea del desglose de una liquidación: una factura que compone la
 * producción bruta del periodo.
 *
 * <p>Versión liviana de la factura (sin ítems): lo necesario para auditar
 * de dónde sale el bruto. La entidad JPA nunca sale por la API.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class SettlementBreakdownLineResponse {

  private UUID invoiceId;
  private String invoiceNumber;
  private UUID patientId;
  private Instant issuedAt;
  private InvoiceStatus status;
  private BigDecimal totalCop;

  public SettlementBreakdownLineResponse() {
  }

  /**
   * Construye la línea desde la factura con su paciente ya cargado.
   *
   * <p>Debe llamarse dentro de una transacción de lectura porque las
   * relaciones se cargan de forma diferida.
   */
  public static SettlementBreakdownLineResponse fromEntity(Invoice invoice) {
    SettlementBreakdownLineResponse r = new SettlementBreakdownLineResponse();
    r.invoiceId = invoice.getId();
    r.invoiceNumber = invoice.getInvoiceNumber();
    r.patientId = invoice.getPatient().getId();
    r.issuedAt = invoice.getIssuedAt();
    r.status = invoice.getStatus();
    r.totalCop = invoice.getTotalCop();
    return r;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public void setInvoiceId(UUID invoiceId) {
    this.invoiceId = invoiceId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public void setStatus(InvoiceStatus status) {
    this.status = status;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }

  public void setTotalCop(BigDecimal totalCop) {
    this.totalCop = totalCop;
  }
}
