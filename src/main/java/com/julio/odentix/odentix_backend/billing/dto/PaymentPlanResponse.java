package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO de salida para un plan de pago completo con sus cuotas (FASE6-02).
 */
public class PaymentPlanResponse {

  private UUID id;
  private UUID treatmentPlanId;
  private BigDecimal totalAmountCop;
  private int installmentsCount;
  private Instant createdAt;
  private List<InstallmentResponse> installments;

  private PaymentPlanResponse() {
  }

  /**
   * Construye el DTO a partir de la entidad y sus cuotas ya cargadas.
   *
   * <p>Las cuotas se pasan explícitamente para evitar cargar la colección
   * lazy desde dentro del factory method (puede estar fuera de sesión).
   */
  public static PaymentPlanResponse fromEntity(PaymentPlan plan, List<InstallmentResponse> installments) {
    PaymentPlanResponse r = new PaymentPlanResponse();
    r.id = plan.getId();
    r.treatmentPlanId = plan.getTreatmentPlanId();
    r.totalAmountCop = plan.getTotalAmountCop();
    r.installmentsCount = plan.getInstallmentsCount();
    r.createdAt = plan.getCreatedAt();
    r.installments = installments;
    return r;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTreatmentPlanId() {
    return treatmentPlanId;
  }

  public BigDecimal getTotalAmountCop() {
    return totalAmountCop;
  }

  public int getInstallmentsCount() {
    return installmentsCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public List<InstallmentResponse> getInstallments() {
    return installments;
  }
}
