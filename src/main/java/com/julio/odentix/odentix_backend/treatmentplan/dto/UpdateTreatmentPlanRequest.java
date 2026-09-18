package com.julio.odentix.odentix_backend.treatmentplan.dto;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de actualización de datos de un plan de tratamiento en borrador (FASE4-02).
 */
public class UpdateTreatmentPlanRequest {

  private UUID professionalId;
  private String diagnosis;
  private List<@Valid CreateTreatmentPlanItemRequest> items;

  public UpdateTreatmentPlanRequest() {
  }

  public UpdateTreatmentPlanRequest(UUID professionalId, String diagnosis, List<CreateTreatmentPlanItemRequest> items) {
    this.professionalId = professionalId;
    this.diagnosis = diagnosis;
    this.items = items;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public List<CreateTreatmentPlanItemRequest> getItems() {
    return items;
  }

  public void setItems(List<CreateTreatmentPlanItemRequest> items) {
    this.items = items;
  }
}
