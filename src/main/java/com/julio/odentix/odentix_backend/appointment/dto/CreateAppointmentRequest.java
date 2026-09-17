package com.julio.odentix.odentix_backend.appointment.dto;

import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud para agendar una nueva cita (FASE3-02).
 */
public class CreateAppointmentRequest {

  @NotNull(message = "El identificador del paciente es obligatorio")
  private UUID patientId;

  private UUID professionalId;

  private UUID roomId;

  private UUID procedureId;

  @NotNull(message = "La fecha y hora de inicio es obligatoria")
  private Instant startsAt;

  @NotNull(message = "La fecha y hora de fin es obligatoria")
  private Instant endsAt;

  @PositiveOrZero(message = "El valor estimado no puede ser negativo")
  private BigDecimal estimatedValueCop;

  private RiskLevel riskLevel;

  private String notes;

  public CreateAppointmentRequest() {}

  public CreateAppointmentRequest(
      UUID patientId,
      UUID professionalId,
      Instant startsAt,
      Instant endsAt) {
    this.patientId = patientId;
    this.professionalId = professionalId;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
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

  public UUID getRoomId() {
    return roomId;
  }

  public void setRoomId(UUID roomId) {
    this.roomId = roomId;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public void setEstimatedValueCop(BigDecimal estimatedValueCop) {
    this.estimatedValueCop = estimatedValueCop;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
