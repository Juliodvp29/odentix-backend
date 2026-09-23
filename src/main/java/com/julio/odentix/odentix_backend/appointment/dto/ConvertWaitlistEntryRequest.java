package com.julio.odentix.odentix_backend.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class ConvertWaitlistEntryRequest {

  @NotNull(message = "La cita origen es obligatoria")
  private UUID sourceAppointmentId;

  private String notes;

  public ConvertWaitlistEntryRequest() {
  }

  public ConvertWaitlistEntryRequest(UUID sourceAppointmentId) {
    this.sourceAppointmentId = sourceAppointmentId;
  }

  public UUID getSourceAppointmentId() {
    return sourceAppointmentId;
  }

  public void setSourceAppointmentId(UUID sourceAppointmentId) {
    this.sourceAppointmentId = sourceAppointmentId;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
