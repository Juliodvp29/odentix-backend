package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.ClinicalRecord;
import java.time.Instant;
import java.util.UUID;

public class ClinicalRecordResponse {

  private UUID id;
  private UUID tenantId;
  private UUID patientId;
  private UUID professionalId;
  private String chiefComplaint;
  private String anamnesis;
  private String diagnosis;
  private String evolution;
  private Instant recordedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public static ClinicalRecordResponse fromEntity(ClinicalRecord record) {
    ClinicalRecordResponse response = new ClinicalRecordResponse();
    response.id = record.getId();
    response.tenantId = record.getTenantId();
    response.patientId = record.getPatient().getId();
    response.professionalId = record.getProfessionalId();
    response.chiefComplaint = record.getChiefComplaint();
    response.anamnesis = record.getAnamnesis();
    response.diagnosis = record.getDiagnosis();
    response.evolution = record.getEvolution();
    response.recordedAt = record.getRecordedAt();
    response.createdAt = record.getCreatedAt();
    response.updatedAt = record.getUpdatedAt();
    return response;
  }

  public UUID getId() {
    return id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public String getChiefComplaint() {
    return chiefComplaint;
  }

  public String getAnamnesis() {
    return anamnesis;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public String getEvolution() {
    return evolution;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
