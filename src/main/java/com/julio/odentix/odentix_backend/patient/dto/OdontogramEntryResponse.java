package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntry;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import java.time.Instant;
import java.util.UUID;

/**
 * Salida de una entrada de odontograma (FASE2-08). La entidad JPA nunca
 * sale por la API: la conversión vive en {@link #fromEntity(OdontogramEntry)}.
 */
public class OdontogramEntryResponse {

  private UUID id;
  private UUID tenantId;
  private UUID patientId;
  private short toothNumber;
  private String surface;
  private OdontogramEntryType entryType;
  private String condition;
  private UUID recordedBy;
  private String notes;
  private Instant recordedAt;
  private Instant createdAt;
  private Instant updatedAt;

  public static OdontogramEntryResponse fromEntity(OdontogramEntry entry) {
    OdontogramEntryResponse response = new OdontogramEntryResponse();
    response.id = entry.getId();
    response.tenantId = entry.getTenantId();
    response.patientId = entry.getPatient().getId();
    response.toothNumber = entry.getToothNumber();
    response.surface = entry.getSurface();
    response.entryType = entry.getEntryType();
    response.condition = entry.getCondition();
    response.recordedBy = entry.getRecordedBy();
    response.notes = entry.getNotes();
    response.recordedAt = entry.getRecordedAt();
    response.createdAt = entry.getCreatedAt();
    response.updatedAt = entry.getUpdatedAt();
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

  public short getToothNumber() {
    return toothNumber;
  }

  public String getSurface() {
    return surface;
  }

  public OdontogramEntryType getEntryType() {
    return entryType;
  }

  public String getCondition() {
    return condition;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public String getNotes() {
    return notes;
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
