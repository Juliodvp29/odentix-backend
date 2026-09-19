package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de email que registra en log en vez de enviar (FASE8-03).
 *
 * <p>Activo cuando el SMTP está apagado (`odentix.notifications.email.enabled=false`,
 * el default): desarrollo, tests y clínicas sin proveedor de email siguen
 * operando y cada intento queda registrado en `notifications` con su resultado.
 * Nunca inventa un envío.
 */
@Component
@ConditionalOnProperty(
    prefix = "odentix.notifications.email",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.email;
  }

  @Override
  public void send(String recipient, String subject, String body) {
    // Sin destinatario en el log (dato personal del paciente, regla §5.5).
    log.info("Notificación email (adaptador de registro): asunto='{}', cuerpo de {} caracteres.",
        subject, body != null ? body.length() : 0);
  }
}
