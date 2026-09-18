package com.julio.odentix.odentix_backend.billing.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plan de pago en cuotas asociado a un plan de tratamiento (FASE6-01).
 *
 * <p>Modela el acuerdo de pago fraccionado que se ofrece al paciente una vez
 * aceptado un {@code TreatmentPlan}. Contiene el monto total pactado y el
 * número de cuotas; las cuotas individuales viven en {@link Installment}.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 * La relación con {@code TreatmentPlan} se modela como UUID simple (no {@code @ManyToOne})
 * para evitar cargar el grafo de tratamiento en operaciones de cartera — mismo
 * patrón que las FK de {@code Invoice} hacia {@code TreatmentPlan}.
 *
 * <p>La cascade {@code ALL} con {@code orphanRemoval} permite que al eliminar un
 * {@code PaymentPlan} se eliminen sus cuotas en cascada; en la práctica los planes
 * de pago no se borran, pero es más seguro que dejar huérfanos.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "payment_plans")
public class PaymentPlan extends TenantAwareEntity {

  @Column(name = "treatment_plan_id", nullable = false)
  private UUID treatmentPlanId;

  @Column(name = "total_amount_cop", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmountCop;

  @Column(name = "installments_count", nullable = false)
  private int installmentsCount;

  @OneToMany(mappedBy = "paymentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("installmentNumber ASC")
  private List<Installment> installments = new ArrayList<>();

  public PaymentPlan() {
    super();
  }

  public UUID getTreatmentPlanId() {
    return treatmentPlanId;
  }

  public void setTreatmentPlanId(UUID treatmentPlanId) {
    this.treatmentPlanId = treatmentPlanId;
  }

  public BigDecimal getTotalAmountCop() {
    return totalAmountCop;
  }

  public void setTotalAmountCop(BigDecimal totalAmountCop) {
    this.totalAmountCop = totalAmountCop;
  }

  public int getInstallmentsCount() {
    return installmentsCount;
  }

  public void setInstallmentsCount(int installmentsCount) {
    this.installmentsCount = installmentsCount;
  }

  public List<Installment> getInstallments() {
    return installments;
  }

  /**
   * Agrega una cuota al plan y establece la relación bidireccional.
   *
   * <p>Siempre usar este método (no manipular la lista directamente) para
   * mantener coherencia entre ambos lados de la relación JPA.
   */
  public void addInstallment(Installment installment) {
    installments.add(installment);
    installment.setPaymentPlan(this);
  }

  /**
   * Elimina una cuota del plan y rompe la relación bidireccional.
   */
  public void removeInstallment(Installment installment) {
    installments.remove(installment);
    installment.setPaymentPlan(null);
  }

  @Override
  public String toString() {
    // Sin montos en logs (datos financieros del tenant, regla §5.5 de AGENTS.md).
    return "PaymentPlan{"
        + "id=" + getId()
        + ", treatmentPlanId=" + treatmentPlanId
        + ", installmentsCount=" + installmentsCount
        + '}';
  }
}
