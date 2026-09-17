package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.PatientFile;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para un archivo de paciente (FASE2-09).
 */
public class PatientFileResponse {

  private UUID id;
  private UUID patientId;
  private String fileName;
  private String contentType;
  private Long sizeBytes;
  private UUID uploadedBy;
  private Instant createdAt;

  public PatientFileResponse() {
  }

  public PatientFileResponse(
      UUID id,
      UUID patientId,
      String fileName,
      String contentType,
      Long sizeBytes,
      UUID uploadedBy,
      Instant createdAt) {
    this.id = id;
    this.patientId = patientId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.uploadedBy = uploadedBy;
    this.createdAt = createdAt;
  }

  public static PatientFileResponse fromEntity(PatientFile entity) {
    return new PatientFileResponse(
        entity.getId(),
        entity.getPatient().getId(),
        entity.getFileName(),
        entity.getContentType(),
        entity.getSizeBytes(),
        entity.getUploadedBy(),
        entity.getCreatedAt());
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public void setSizeBytes(Long sizeBytes) {
    this.sizeBytes = sizeBytes;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(UUID uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
