package com.julio.odentix.odentix_backend.appointment.dto;

import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Petición de cambio de estado de una cita (FASE3-04).
 *
 * <p>Solo transporta el estado destino; las reglas de qué transiciones son
 * válidas viven en {@code AppointmentService}, no en este DTO.
 */
public class UpdateAppointmentStatusRequest {

  @NotNull(message = "El estado destino es obligatorio")
  private AppointmentStatus status;

  public UpdateAppointmentStatusRequest() {
  }

  public UpdateAppointmentStatusRequest(AppointmentStatus status) {
    this.status = status;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus status) {
    this.status = status;
  }
}
