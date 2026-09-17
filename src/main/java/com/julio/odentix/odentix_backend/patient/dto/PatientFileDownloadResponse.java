package com.julio.odentix.odentix_backend.patient.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO de respuesta para la URL de descarga de un archivo de paciente (FASE2-10).
 */
public class PatientFileDownloadResponse {

  private UUID fileId;
  private String fileName;
  private String downloadUrl;
  private Instant expiresAt;

  public PatientFileDownloadResponse() {
  }

  public PatientFileDownloadResponse(UUID fileId, String fileName, String downloadUrl, Instant expiresAt) {
    this.fileId = fileId;
    this.fileName = fileName;
    this.downloadUrl = downloadUrl;
    this.expiresAt = expiresAt;
  }

  public UUID getFileId() {
    return fileId;
  }

  public void setFileId(UUID fileId) {
    this.fileId = fileId;
  }

  public String getFileName() {
    return fileName;
  }

  public void setFileName(String fileName) {
    this.fileName = fileName;
  }

  public String getDownloadUrl() {
    return downloadUrl;
  }

  public void setDownloadUrl(String downloadUrl) {
    this.downloadUrl = downloadUrl;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
  }
}
