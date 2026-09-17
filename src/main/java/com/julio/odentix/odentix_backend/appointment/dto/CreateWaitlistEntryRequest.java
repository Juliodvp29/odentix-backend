package com.julio.odentix.odentix_backend.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

/**
 * Petición de registro en lista de espera (FASE3-06).
 *
 * <p>Solo el paciente es obligatorio: el procedimiento de interés y el
 * rango de fechas deseado son opcionales. El estado no se acepta del
 * cliente — toda entrada nace {@code activa}.
 */
public class CreateWaitlistEntryRequest {

  @NotNull(message = "El paciente es obligatorio")
  private UUID patientId;

  private UUID procedureId;

  private Instant desiredFrom;

  private Instant desiredTo;

  public CreateWaitlistEntryRequest() {
  }

  public CreateWaitlistEntryRequest(UUID patientId, UUID procedureId, Instant desiredFrom, Instant desiredTo) {
    this.patientId = patientId;
    this.procedureId = procedureId;
    this.desiredFrom = desiredFrom;
    this.desiredTo = desiredTo;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Instant getDesiredFrom() {
    return desiredFrom;
  }

  public void setDesiredFrom(Instant desiredFrom) {
    this.desiredFrom = desiredFrom;
  }

  public Instant getDesiredTo() {
    return desiredTo;
  }

  public void setDesiredTo(Instant desiredTo) {
    this.desiredTo = desiredTo;
  }
}
