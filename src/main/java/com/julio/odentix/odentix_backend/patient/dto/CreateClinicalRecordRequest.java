package com.julio.odentix.odentix_backend.patient.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public class CreateClinicalRecordRequest {

  @NotBlank(message = "El motivo de consulta no puede estar vacío")
  private String chiefComplaint;
  private String anamnesis;
  private String diagnosis;
  private String evolution;
  private Instant recordedAt;
  private UUID professionalId;

  public String getChiefComplaint() {
    return chiefComplaint;
  }

  public void setChiefComplaint(String chiefComplaint) {
    this.chiefComplaint = chiefComplaint;
  }

  public String getAnamnesis() {
    return anamnesis;
  }

  public void setAnamnesis(String anamnesis) {
    this.anamnesis = anamnesis;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public String getEvolution() {
    return evolution;
  }

  public void setEvolution(String evolution) {
    this.evolution = evolution;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }
}
