package com.julio.odentix.odentix_backend.specialist.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Liquidación por periodo de un especialista externo (FASE7-01).
 *
 * <p>Registra la producción bruta del periodo y el monto de honorarios a pagar.
 * El cálculo de {@code gross_production_cop} a partir de tratamientos/citas
 * facturados llega en FASE7-02; aquí solo el modelo con su invariante
 * ({@code period_end >= period_start}, también como CHECK en BD).
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "specialist_settlements")
public class SpecialistSettlement extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "specialist_id", nullable = false)
  private Specialist specialist;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "gross_production_cop", nullable = false, precision = 12, scale = 2)
  private BigDecimal grossProductionCop = BigDecimal.ZERO;

  @Column(name = "fee_amount_cop", nullable = false, precision = 12, scale = 2)
  private BigDecimal feeAmountCop = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private SettlementStatus status = SettlementStatus.pendiente;

  /**
   * Momento en que se pagó la liquidación. Nulo mientras esté pendiente.
   */
  @Column(name = "paid_at")
  private Instant paidAt;

  public SpecialistSettlement() {
    super();
  }

  public SpecialistSettlement(UUID tenantId, Specialist specialist,
      LocalDate periodStart, LocalDate periodEnd) {
    super(tenantId);
    this.specialist = specialist;
    this.periodStart = periodStart;
    this.periodEnd = periodEnd;
  }

  /**
   * Validación de aplicación del rango del periodo (el CHECK de BD es la
   * capa autoritativa; esta es defensa en profundidad con mensaje claro).
   */
  @PrePersist
  @PreUpdate
  protected void validarPeriodo() {
    if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) {
      throw new IllegalStateException("period_end debe ser mayor o igual a period_start");
    }
  }

  public Specialist getSpecialist() {
    return specialist;
  }

  public void setSpecialist(Specialist specialist) {
    this.specialist = specialist;
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
    this.status = status != null ? status : SettlementStatus.pendiente;
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
    return "SpecialistSettlement{"
        + "id=" + getId()
        + ", status=" + status
        + ", periodStart=" + periodStart
        + ", periodEnd=" + periodEnd
        + '}';
  }
}
