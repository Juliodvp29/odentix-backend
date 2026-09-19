package com.julio.odentix.odentix_backend.subscription.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Suscripción de un tenant (clínica) a un {@link Plan} (FASE11-01).
 *
 * <p>Tabla de negocio: hereda de {@link TenantAwareEntity}, así el filtro
 * automático por {@code tenant_id} (@TenantId) y el RLS de V25 aplican sin
 * configuración adicional. Un tenant no puede tener dos suscripciones "vivas"
 * ({@code trialing}, {@code active}, {@code past_due}) al mismo tiempo
 * —restricción en BD vía índice único parcial.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "tenant_subscriptions")
public class TenantSubscription extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private SubscriptionStatus status = SubscriptionStatus.trialing;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "billing_cycle", nullable = false)
  private BillingCycle billingCycle = BillingCycle.monthly;

  @Column(name = "current_period_start", nullable = false)
  private Instant currentPeriodStart;

  @Column(name = "current_period_end", nullable = false)
  private Instant currentPeriodEnd;

  @Column(name = "cancel_at_period_end", nullable = false)
  private boolean cancelAtPeriodEnd;

  public TenantSubscription() {
    super();
  }

  public TenantSubscription(UUID tenantId, Plan plan, Instant currentPeriodEnd) {
    super(tenantId);
    this.plan = plan;
    this.status = SubscriptionStatus.trialing;
    this.billingCycle = BillingCycle.monthly;
    this.currentPeriodStart = Instant.now();
    this.currentPeriodEnd = currentPeriodEnd;
    this.cancelAtPeriodEnd = false;
  }

  /**
   * Indica si la suscripción se considera "viva" (impide una segunda
   * suscripción simultánea según el índice parcial de V25).
   *
   * @return true si el estado es trialing, active o past_due.
   */
  public boolean isLive() {
    return status == SubscriptionStatus.trialing
        || status == SubscriptionStatus.active
        || status == SubscriptionStatus.past_due;
  }

  public Plan getPlan() {
    return plan;
  }

  public void setPlan(Plan plan) {
    this.plan = plan;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status != null ? status : SubscriptionStatus.trialing;
  }

  public BillingCycle getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(BillingCycle billingCycle) {
    this.billingCycle = billingCycle != null ? billingCycle : BillingCycle.monthly;
  }

  public Instant getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public void setCurrentPeriodStart(Instant currentPeriodStart) {
    this.currentPeriodStart = currentPeriodStart;
  }

  public Instant getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public boolean isCancelAtPeriodEnd() {
    return cancelAtPeriodEnd;
  }

  public void setCancelAtPeriodEnd(boolean cancelAtPeriodEnd) {
    this.cancelAtPeriodEnd = cancelAtPeriodEnd;
  }

  @Override
  public String toString() {
    return "TenantSubscription{"
        + "id=" + getId()
        + ", tenantId=" + getTenantId()
        + ", status=" + status
        + ", billingCycle=" + billingCycle
        + ", cancelAtPeriodEnd=" + cancelAtPeriodEnd
        + '}';
  }
}
