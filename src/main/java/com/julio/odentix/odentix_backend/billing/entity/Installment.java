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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Cuota individual dentro de un plan de pago (FASE6-01).
 *
 * <p>Cada cuota tiene un número de orden, monto, fecha de vencimiento y estado.
 * El estado inicial siempre es {@code pendiente}; el job diario de FASE6-03
 * transiciona a {@code vencida} cuándo {@code due_date < CURRENT_DATE} (vía la
 * función PG {@code mark_overdue_installments()}). El endpoint de FASE6-02
 * transiciona a {@code pagada}.
 *
 * <p>La constraint {@code UNIQUE (payment_plan_id, installment_number)} está
 * definida en la BD (V18) y también declarada aquí para que Hibernate la
 * considere en el DDL de validación. Impide generar cuotas duplicadas dentro
 * del mismo plan.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(
    name = "installments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_installments_plan_number",
            columnNames = {"payment_plan_id", "installment_number"})
    }
)
public class Installment extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "payment_plan_id", nullable = false)
  private PaymentPlan paymentPlan;

  @Column(name = "installment_number", nullable = false)
  private int installmentNumber;

  @Column(name = "amount_cop", nullable = false, precision = 12, scale = 2)
  private BigDecimal amountCop;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private InstallmentStatus status = InstallmentStatus.pendiente;

  /**
   * Momento en que se registró el pago de esta cuota. Nulo mientras no esté pagada.
   */
  @Column(name = "paid_at")
  private Instant paidAt;

  public Installment() {
    super();
  }

  public PaymentPlan getPaymentPlan() {
    return paymentPlan;
  }

  public void setPaymentPlan(PaymentPlan paymentPlan) {
    this.paymentPlan = paymentPlan;
  }

  public int getInstallmentNumber() {
    return installmentNumber;
  }

  public void setInstallmentNumber(int installmentNumber) {
    this.installmentNumber = installmentNumber;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public void setAmountCop(BigDecimal amountCop) {
    this.amountCop = amountCop;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  public InstallmentStatus getStatus() {
    return status;
  }

  public void setStatus(InstallmentStatus status) {
    this.status = status != null ? status : InstallmentStatus.pendiente;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public void setPaidAt(Instant paidAt) {
    this.paidAt = paidAt;
  }

  @Override
  public String toString() {
    // Sin montos en logs (datos financieros del tenant, regla §5.5 de AGENTS.md).
    return "Installment{"
        + "id=" + getId()
        + ", installmentNumber=" + installmentNumber
        + ", status=" + status
        + ", dueDate=" + dueDate
        + '}';
  }
}
