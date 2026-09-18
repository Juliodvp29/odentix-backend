package com.julio.odentix.odentix_backend.treatmentplan.dto;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para avanzar o transicionar el estado de un plan de tratamiento (FASE4-02).
 */
public class UpdateTreatmentPlanStatusRequest {

  @NotNull(message = "El nuevo estado del plan de tratamiento es obligatorio")
  private TreatmentPlanStatus status;

  public UpdateTreatmentPlanStatusRequest() {
  }

  public UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus status) {
    this.status = status;
  }

  public TreatmentPlanStatus getStatus() {
    return status;
  }

  public void setStatus(TreatmentPlanStatus status) {
    this.status = status;
  }
}
