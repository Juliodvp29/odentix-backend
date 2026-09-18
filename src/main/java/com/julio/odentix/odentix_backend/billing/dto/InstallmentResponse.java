package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de salida para una cuota individual de un plan de pago (FASE6-02).
 *
 * <p>No expone la entidad JPA directamente — patrón estándar del proyecto (§9 AGENTS.md).
 */
public class InstallmentResponse {

  private UUID id;
  private UUID paymentPlanId;
  private int installmentNumber;
  private BigDecimal amountCop;
  private LocalDate dueDate;
  private InstallmentStatus status;
  private Instant paidAt;

  private InstallmentResponse() {
  }

  /**
   * Construye el DTO a partir de la entidad.
   */
  public static InstallmentResponse fromEntity(Installment installment) {
    InstallmentResponse r = new InstallmentResponse();
    r.id = installment.getId();
    r.paymentPlanId = installment.getPaymentPlan() != null
        ? installment.getPaymentPlan().getId()
        : null;
    r.installmentNumber = installment.getInstallmentNumber();
    r.amountCop = installment.getAmountCop();
    r.dueDate = installment.getDueDate();
    r.status = installment.getStatus();
    r.paidAt = installment.getPaidAt();
    return r;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPaymentPlanId() {
    return paymentPlanId;
  }

  public int getInstallmentNumber() {
    return installmentNumber;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public InstallmentStatus getStatus() {
    return status;
  }

  public Instant getPaidAt() {
    return paidAt;
  }
}
