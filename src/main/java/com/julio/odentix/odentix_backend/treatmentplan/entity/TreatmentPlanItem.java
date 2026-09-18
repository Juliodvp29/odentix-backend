package com.julio.odentix.odentix_backend.treatmentplan.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Procedimiento o ítem incluido dentro de un plan de tratamiento odontológico (FASE4-01).
 *
 * <p>Representa una intervención clínica propuesta (opcionalmente asociada a una pieza
 * dental según la notación FDI de dos dígitos: 11-48), con su precio y descuento en COP.
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento multi-tenant automático
 * por {@code tenant_id}.
 *
 * <p>{@code procedureId} es un UUID nullable sin FK estricta hasta que exista la tabla de
 * catálogo de procedimientos (mismo precedente que en citas y lista de espera).
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "treatment_plan_items")
public class TreatmentPlanItem extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "treatment_plan_id", nullable = false)
  private TreatmentPlan treatmentPlan;

  @Column(name = "procedure_id")
  private UUID procedureId;

  @Column(name = "tooth_number")
  private Short toothNumber;

  @Column(name = "price_cop", precision = 12, scale = 2, nullable = false)
  private BigDecimal priceCop = BigDecimal.ZERO;

  @Column(name = "discount_cop", precision = 12, scale = 2, nullable = false)
  private BigDecimal discountCop = BigDecimal.ZERO;

  public TreatmentPlanItem() {
    super();
  }

  public TreatmentPlanItem(UUID tenantId, TreatmentPlan treatmentPlan, BigDecimal priceCop) {
    super(tenantId);
    this.treatmentPlan = treatmentPlan;
    this.priceCop = priceCop != null ? priceCop : BigDecimal.ZERO;
    this.discountCop = BigDecimal.ZERO;
  }

  public TreatmentPlanItem(
      UUID tenantId,
      TreatmentPlan treatmentPlan,
      UUID procedureId,
      Short toothNumber,
      BigDecimal priceCop,
      BigDecimal discountCop) {
    super(tenantId);
    this.treatmentPlan = treatmentPlan;
    this.procedureId = procedureId;
    this.toothNumber = toothNumber;
    this.priceCop = priceCop != null ? priceCop : BigDecimal.ZERO;
    this.discountCop = discountCop != null ? discountCop : BigDecimal.ZERO;
  }

  public TreatmentPlan getTreatmentPlan() {
    return treatmentPlan;
  }

  public void setTreatmentPlan(TreatmentPlan treatmentPlan) {
    this.treatmentPlan = treatmentPlan;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Short getToothNumber() {
    return toothNumber;
  }

  public void setToothNumber(Short toothNumber) {
    this.toothNumber = toothNumber;
  }

  public BigDecimal getPriceCop() {
    return priceCop;
  }

  public void setPriceCop(BigDecimal priceCop) {
    this.priceCop = priceCop != null ? priceCop : BigDecimal.ZERO;
  }

  public BigDecimal getDiscountCop() {
    return discountCop;
  }

  public void setDiscountCop(BigDecimal discountCop) {
    this.discountCop = discountCop != null ? discountCop : BigDecimal.ZERO;
  }

  /**
   * Calcula el precio neto del ítem: {@code price_cop - discount_cop}.
   *
   * @return valor neto en COP (no menor a cero si los valores respetan las restricciones).
   */
  public BigDecimal getNetPrice() {
    BigDecimal price = priceCop != null ? priceCop : BigDecimal.ZERO;
    BigDecimal discount = discountCop != null ? discountCop : BigDecimal.ZERO;
    return price.subtract(discount);
  }
}
