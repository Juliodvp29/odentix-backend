package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Datos para programar opcionalmente una cita inicial al convertir un lead en paciente (FASE5-03).
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
public class ConvertLeadAppointmentData {

  private UUID professionalId;
  private UUID roomId;
  private UUID procedureId;

  @NotNull(message = "La fecha y hora de inicio de la cita es obligatoria si se incluye cita")
  private Instant startsAt;

  @NotNull(message = "La fecha y hora de fin de la cita es obligatoria si se incluye cita")
  private Instant endsAt;

  @PositiveOrZero(message = "El valor estimado no puede ser negativo")
  private BigDecimal estimatedValueCop;

  private RiskLevel riskLevel;
  private String notes;

  public ConvertLeadAppointmentData() {
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
