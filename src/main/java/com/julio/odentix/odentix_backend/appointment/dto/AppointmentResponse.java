package com.julio.odentix.odentix_backend.appointment.dto;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación de respuesta para una cita odontológica (FASE3-02).
 */
public class AppointmentResponse {

  private UUID id;
  private UUID patientId;
  private String patientName;
  private UUID professionalId;
  private String professionalName;
  private UUID roomId;
  private String roomName;
  private UUID procedureId;
  private Instant startsAt;
  private Instant endsAt;
  private BigDecimal estimatedValueCop;
  private RiskLevel riskLevel;
  private AppointmentStatus status;
  private String notes;
  private Instant createdAt;
  private Instant updatedAt;

  public static AppointmentResponse fromEntity(Appointment appointment) {
    AppointmentResponse response = new AppointmentResponse();
    response.id = appointment.getId();
    response.startsAt = appointment.getStartsAt();
    response.endsAt = appointment.getEndsAt();
    response.estimatedValueCop = appointment.getEstimatedValueCop();
    response.riskLevel = appointment.getRiskLevel();
    response.status = appointment.getStatus();
    response.notes = appointment.getNotes();
    response.procedureId = appointment.getProcedureId();
    response.createdAt = appointment.getCreatedAt();
    response.updatedAt = appointment.getUpdatedAt();

    if (appointment.getPatient() != null) {
      response.patientId = appointment.getPatient().getId();
      response.patientName = appointment.getPatient().getFirstName() + " " + appointment.getPatient().getLastName();
    }

    if (appointment.getProfessional() != null) {
      response.professionalId = appointment.getProfessional().getId();
      response.professionalName = appointment.getProfessional().getFullName();
    }

    if (appointment.getRoom() != null) {
      response.roomId = appointment.getRoom().getId();
      response.roomName = appointment.getRoom().getName();
    }

    return response;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public String getPatientName() {
    return patientName;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public String getProfessionalName() {
    return professionalName;
  }

  public UUID getRoomId() {
    return roomId;
  }

  public String getRoomName() {
    return roomName;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
