package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.entity.Payment;
import com.julio.odentix.odentix_backend.billing.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Salida de un pago registrado (FASE4-04). Incluye el estado recalculado
 * de la factura para observar la transición sin necesidad de un GET.
 */
public class PaymentResponse {

  private final UUID id;
  private final UUID tenantId;
  private final UUID invoiceId;
  private final BigDecimal amountCop;
  private final PaymentMethod method;
  private final String reference;
  private final Instant paidAt;
  private final InvoiceStatus invoiceStatus;

  public PaymentResponse(
      UUID id,
      UUID tenantId,
      UUID invoiceId,
      BigDecimal amountCop,
      PaymentMethod method,
      String reference,
      Instant paidAt,
      InvoiceStatus invoiceStatus) {
    this.id = id;
    this.tenantId = tenantId;
    this.invoiceId = invoiceId;
    this.amountCop = amountCop;
    this.method = method;
    this.reference = reference;
    this.paidAt = paidAt;
    this.invoiceStatus = invoiceStatus;
  }

  public static PaymentResponse fromEntity(Payment payment, InvoiceStatus invoiceStatus) {
    return new PaymentResponse(
        payment.getId(),
        payment.getTenantId(),
        payment.getInvoice().getId(),
        payment.getAmountCop(),
        payment.getMethod(),
        payment.getReference(),
        payment.getPaidAt(),
        invoiceStatus);
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getInvoiceId() {
    return invoiceId;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public String getReference() {
    return reference;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public InvoiceStatus getInvoiceStatus() {
    return invoiceStatus;
  }
}
