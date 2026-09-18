package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Salida de una factura con sus ítems (FASE4-04). La entidad JPA nunca
 * sale por la API.
 */
public class InvoiceResponse {

  private final UUID id;
  private final UUID tenantId;
  private final UUID patientId;
  private final UUID treatmentPlanId;
  private final String invoiceNumber;
  private final InvoiceStatus status;
  private final BigDecimal subtotalCop;
  private final BigDecimal discountCop;
  private final BigDecimal totalCop;
  private final Instant issuedAt;
  private final List<InvoiceItemResponse> items;

  public InvoiceResponse(
      UUID id,
      UUID tenantId,
      UUID patientId,
      UUID treatmentPlanId,
      String invoiceNumber,
      InvoiceStatus status,
      BigDecimal subtotalCop,
      BigDecimal discountCop,
      BigDecimal totalCop,
      Instant issuedAt,
      List<InvoiceItemResponse> items) {
    this.id = id;
    this.tenantId = tenantId;
    this.patientId = patientId;
    this.treatmentPlanId = treatmentPlanId;
    this.invoiceNumber = invoiceNumber;
    this.status = status;
    this.subtotalCop = subtotalCop;
    this.discountCop = discountCop;
    this.totalCop = totalCop;
    this.issuedAt = issuedAt;
    this.items = items;
  }

  public static InvoiceResponse fromEntity(Invoice invoice, List<InvoiceItemResponse> items) {
    return new InvoiceResponse(
        invoice.getId(),
        invoice.getTenantId(),
        invoice.getPatient().getId(),
        invoice.getTreatmentPlan() != null ? invoice.getTreatmentPlan().getId() : null,
        invoice.getInvoiceNumber(),
        invoice.getStatus(),
        invoice.getSubtotalCop(),
        invoice.getDiscountCop(),
        invoice.getTotalCop(),
        invoice.getIssuedAt(),
        items);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getTreatmentPlanId() {
    return treatmentPlanId;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public BigDecimal getSubtotalCop() {
    return subtotalCop;
  }

  public BigDecimal getDiscountCop() {
    return discountCop;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public List<InvoiceItemResponse> getItems() {
    return items;
  }
}
