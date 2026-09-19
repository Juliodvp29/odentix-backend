package com.julio.odentix.odentix_backend.saas.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
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
 * Cobro del SaaS a una clínica vía Bold (FASE11-04).
 *
 * <p>Cada ciclo (mensual/anual) se cobra con un link de pago de Bold (API Link
 * de pagos: Bold.co no tiene suscripciones recurrentes nativas). `boldReference`
 * es nuestra referencia única enviada a Bold y `boldNotificationId` da
 * idempotencia ante reintentos del webhook.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "saas_payments")
public class SaasPayment extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  private TenantSubscription subscription;

  @Column(name = "bold_reference", nullable = false, unique = true)
  private String boldReference;

  @Column(name = "bold_payment_link")
  private String boldPaymentLink;

  @Column(name = "bold_notification_id", unique = true)
  private String boldNotificationId;

  @Column(name = "amount_cop", nullable = false, precision = 12, scale = 2)
  private BigDecimal amountCop;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "billing_cycle", nullable = false)
  private BillingCycle billingCycle;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private SaasPaymentStatus status = SaasPaymentStatus.pendiente;

  @Column(name = "paid_at")
  private Instant paidAt;

  public SaasPayment() {
    super();
  }

  public SaasPayment(UUID tenantId, TenantSubscription subscription, String boldReference,
      BigDecimal amountCop, BillingCycle billingCycle) {
    super(tenantId);
    this.subscription = subscription;
    this.boldReference = boldReference;
    this.amountCop = amountCop;
    this.billingCycle = billingCycle;
  }

  public TenantSubscription getSubscription() {
    return subscription;
  }

  public void setSubscription(TenantSubscription subscription) {
    this.subscription = subscription;
  }

  public String getBoldReference() {
    return boldReference;
  }

  public void setBoldReference(String boldReference) {
    this.boldReference = boldReference;
  }

  public String getBoldPaymentLink() {
    return boldPaymentLink;
  }

  public void setBoldPaymentLink(String boldPaymentLink) {
    this.boldPaymentLink = boldPaymentLink;
  }

  public String getBoldNotificationId() {
    return boldNotificationId;
  }

  public void setBoldNotificationId(String boldNotificationId) {
    this.boldNotificationId = boldNotificationId;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public void setAmountCop(BigDecimal amountCop) {
    this.amountCop = amountCop;
  }

  public BillingCycle getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(BillingCycle billingCycle) {
    this.billingCycle = billingCycle;
  }

  public SaasPaymentStatus getStatus() {
    return status;
  }

  public void setStatus(SaasPaymentStatus status) {
    this.status = status != null ? status : SaasPaymentStatus.pendiente;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  @Override
  public String toString() {
    // Sin montos en logs (datos financieros, regla §5.5 de AGENTS.md).
    return "SaasPayment{"
        + "id=" + getId()
        + ", status=" + status
        + ", billingCycle=" + billingCycle
        + '}';
  }
}
