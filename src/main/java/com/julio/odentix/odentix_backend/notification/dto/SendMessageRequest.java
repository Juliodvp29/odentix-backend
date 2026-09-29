package com.julio.odentix.odentix_backend.notification.dto;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Envío de un mensaje revisado por un humano (flujo del asistente).
 *
 * <p>El destinatario nunca viene del cliente: se resuelve en el servidor
 * desde la cita indicada (email o teléfono del paciente, según el canal).
 * Sin Lombok.
 */
public class SendMessageRequest {

  @NotNull(message = "appointmentId es obligatorio")
  private UUID appointmentId;

  @NotNull(message = "channel es obligatorio")
  private NotificationChannel channel;

  private String subject;

  @NotBlank(message = "body es obligatorio")
  private String body;

  public SendMessageRequest() {
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public void setChannel(NotificationChannel channel) {
    this.channel = channel;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }
}
