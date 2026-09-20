package com.julio.odentix.odentix_backend.patient.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Metadatos de un archivo adjunto de un paciente (FASE2-09).
 *
 * <p>El binario se almacena en el servicio compatible con S3 referenciado por {@code storageKey}.
 * Esta entidad guarda metadatos, autor y auditoría dentro de PostgreSQL.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "patient_files")
public class PatientFile extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(name = "storage_key", nullable = false)
  private String storageKey;

  @Column(name = "file_name", nullable = false)
  private String fileName;

  @Column(name = "content_type")
  private String contentType;

  @Column(name = "size_bytes")
  private Long sizeBytes;

  @Column(name = "uploaded_by")
  private UUID uploadedBy;

  public PatientFile() {
  }

  public PatientFile(
      UUID tenantId,
      Patient patient,
      String storageKey,
      String fileName,
      String contentType,
      Long sizeBytes,
      UUID uploadedBy) {
    super(tenantId);
    this.patient = patient;
    this.storageKey = storageKey;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.uploadedBy = uploadedBy;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public void setStorageKey(String storageKey) {
    this.storageKey = storageKey;
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
}

