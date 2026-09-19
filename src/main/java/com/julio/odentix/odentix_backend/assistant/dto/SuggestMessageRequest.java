package com.julio.odentix.odentix_backend.assistant.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Solicitud de mensaje sugerido (FASE10-02).
 *
 * <p>Por ahora el único contexto soportado es una cita (típicamente sin
 * confirmar); `hint` permite matizar el tono o el motivo. Sin Lombok (§9).
 */
public class SuggestMessageRequest {

  private UUID appointmentId;

  @Size(max = 200, message = "hint no puede superar 200 caracteres")
  private String hint;

  public SuggestMessageRequest() {
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public String getHint() {
    return hint;
  }

  public void setHint(String hint) {
    this.hint = hint;
  }
}
