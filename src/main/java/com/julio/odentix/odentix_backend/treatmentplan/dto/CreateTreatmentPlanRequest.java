package com.julio.odentix.odentix_backend.treatmentplan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Solicitud de creación de un nuevo plan de tratamiento (FASE4-02).
 */
public class CreateTreatmentPlanRequest {

  @NotNull(message = "El identificador del paciente es obligatorio")
  private UUID patientId;

  private UUID professionalId;

  private String diagnosis;

  @NotEmpty(message = "El plan de tratamiento debe contener al menos un ítem")
  private List<@Valid CreateTreatmentPlanItemRequest> items = new ArrayList<>();

  public CreateTreatmentPlanRequest() {
  }

  public CreateTreatmentPlanRequest(UUID patientId, UUID professionalId, String diagnosis, List<CreateTreatmentPlanItemRequest> items) {
    this.patientId = patientId;
    this.professionalId = professionalId;
    this.diagnosis = diagnosis;
    this.items = items != null ? items : new ArrayList<>();
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
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
    this.items = items != null ? items : new ArrayList<>();
  }
}
