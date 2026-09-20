package com.julio.odentix.odentix_backend.billing.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Pago contra una factura (FASE4-03).
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por
 * tenant. El cambio de estado de la factura según pagos acumulados lo
 * implementa el servicio en FASE4-04.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "payments")
public class Payment extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Column(name = "amount_cop", nullable = false)
  private BigDecimal amountCop;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "method", nullable = false)
  private PaymentMethod method;

  @Column(name = "reference")
  private String reference;

  @Column(name = "paid_at", nullable = false)
  private Instant paidAt;

  public Payment() {
    super();
  }

  public Payment(UUID tenantId, Invoice invoice, BigDecimal amountCop, PaymentMethod method) {
    super(tenantId);
    this.invoice = invoice;
    this.amountCop = amountCop;
    this.method = method;
    this.paidAt = Instant.now();
  }

  public Invoice getInvoice() {
    return invoice;
  }

  public void setInvoice(Invoice invoice) {
    this.invoice = invoice;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public void setAmountCop(BigDecimal amountCop) {
    this.amountCop = amountCop;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public void setMethod(PaymentMethod method) {
    this.method = method;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  @Override
  public String toString() {
    return "Payment{"
        + "id=" + getId()
        + ", method=" + method
        + '}';
  }
}

