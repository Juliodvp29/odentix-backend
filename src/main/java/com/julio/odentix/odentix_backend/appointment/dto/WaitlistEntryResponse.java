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
  private final Instant createdAt;
  private final Instant updatedAt;
  private final Instant contactedAt;
  private final Instant convertedAt;
  private final Instant discardedAt;
  private final String discardReason;
  private final UUID convertedAppointmentId;

  public WaitlistEntryResponse(
      UUID id,
      UUID tenantId,
      UUID patientId,
      String patientName,
      String patientPhone,
      UUID procedureId,
      Instant desiredFrom,
      Instant desiredTo,
      WaitlistStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant contactedAt,
      Instant convertedAt,
      Instant discardedAt,
      String discardReason,
      UUID convertedAppointmentId) {
    this.id = id;
    this.tenantId = tenantId;
    this.patientId = patientId;
    this.patientName = patientName;
    this.patientPhone = patientPhone;
    this.procedureId = procedureId;
    this.desiredFrom = desiredFrom;
    this.desiredTo = desiredTo;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
    this.contactedAt = contactedAt;
    this.convertedAt = convertedAt;
    this.discardedAt = discardedAt;
    this.discardReason = discardReason;
    this.convertedAppointmentId = convertedAppointmentId;
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
        entry.getStatus(),
        entry.getCreatedAt(),
        entry.getUpdatedAt(),
        entry.getContactedAt(),
        entry.getConvertedAt(),
        entry.getDiscardedAt(),
        entry.getDiscardReason(),
        entry.getConvertedAppointment() != null ? entry.getConvertedAppointment().getId() : null);
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getContactedAt() {
    return contactedAt;
  }

  public Instant getConvertedAt() {
    return convertedAt;
  }

  public Instant getDiscardedAt() {
    return discardedAt;
  }

  public String getDiscardReason() {
    return discardReason;
  }

  public UUID getConvertedAppointmentId() {
    return convertedAppointmentId;
  }
}
