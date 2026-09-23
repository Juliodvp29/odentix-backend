package com.julio.odentix.odentix_backend.appointment.dto;

import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Salida de una entrada de lista de espera (FASE3-06). La entidad JPA
 * nunca sale por la API: la conversión vive en {@link #fromEntity}.
 */
public class WaitlistEntryResponse {

  private final UUID id;
  private final UUID tenantId;
  private final UUID patientId;
  private final String patientName;
  private final String patientPhone;
  private final UUID procedureId;
  private final Instant desiredFrom;
  private final Instant desiredTo;
  private final WaitlistStatus status;

  public WaitlistEntryResponse(
      UUID id,
      UUID tenantId,
      UUID patientId,
      String patientName,
      String patientPhone,
      UUID procedureId,
      Instant desiredFrom,
      Instant desiredTo,
      WaitlistStatus status) {
    this.id = id;
    this.tenantId = tenantId;
    this.patientId = patientId;
    this.patientName = patientName;
    this.patientPhone = patientPhone;
    this.procedureId = procedureId;
    this.desiredFrom = desiredFrom;
    this.desiredTo = desiredTo;
    this.status = status;
  }

  public static WaitlistEntryResponse fromEntity(WaitlistEntry entry) {
    return new WaitlistEntryResponse(
        entry.getId(),
        entry.getTenantId(),
        entry.getPatient().getId(),
        entry.getPatient().getFirstName() + " " + entry.getPatient().getLastName(),
        entry.getPatient().getPhone(),
        entry.getProcedureId(),
        entry.getDesiredFrom(),
        entry.getDesiredTo(),
        entry.getStatus());
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

  public String getPatientName() {
    return patientName;
  }

  public String getPatientPhone() {
    return patientPhone;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public Instant getDesiredFrom() {
    return desiredFrom;
  }

  public Instant getDesiredTo() {
    return desiredTo;
  }

  public WaitlistStatus getStatus() {
    return status;
  }
}
