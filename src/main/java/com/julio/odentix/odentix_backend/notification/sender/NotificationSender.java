package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;

/**
 * Contrato para enviar notificaciones por un canal (FASE8-03).
 *
 * <p>Desacopla el dominio del proveedor concreto (SMTP hoy, WhatsApp Business
 * API en FASE8-05): el servicio decide qué enviar y el adaptador activo decide
 * cómo. Cada intento —exitoso o no— lo registra {@code NotificationService};
 * el adaptador solo transporta y reporta fallos con {@link NotificationException}.
 */
public interface NotificationSender {

  /**
   * Canal que atiende este adaptador.
   */
  NotificationChannel channel();

  /**
   * Envía un mensaje de texto plano.
   *
   * @param recipient destinatario en formato del canal (email, teléfono…).
   * @param subject asunto (los canales sin asunto lo ignoran).
   * @param body cuerpo del mensaje.
   * @throws NotificationException si el proveedor falla (timeout, rechazo…).
   */
  void send(String recipient, String subject, String body) throws NotificationException;
}
