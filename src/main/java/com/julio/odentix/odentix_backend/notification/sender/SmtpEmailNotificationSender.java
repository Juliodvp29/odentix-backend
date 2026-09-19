package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adaptador de email vía SMTP (FASE8-03).
 *
 * <p>Se activa con {@code odentix.notifications.email.enabled=true}; toda la
 * configuración sale de variables de entorno (`spring.mail.*` +
 * `odentix.notifications.email.from`) — nunca hay credenciales en el repo
 * (regla §8 de AGENTS.md). Los timeouts SMTP son cortos (ver
 * `application.yml`): un proveedor caído falla rápido y el servicio lo
 * registra como intento `fallida` sin bloquear el flujo de negocio.
 */
@Component
@ConditionalOnProperty(
    prefix = "odentix.notifications.email",
    name = "enabled",
    havingValue = "true")
public class SmtpEmailNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final String from;

  public SmtpEmailNotificationSender(
      JavaMailSender mailSender,
      @Value("${odentix.notifications.email.from:}") String from) {
    this.mailSender = mailSender;
    this.from = from != null ? from.strip() : "";
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.email;
  }

  @Override
  public void send(String recipient, String subject, String body) throws NotificationException {
    if (from.isEmpty()) {
      throw new NotificationException(
          "Falta configurar odentix.notifications.email.from (NOTIFICATIONS_EMAIL_FROM).");
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      message.setTo(recipient);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
    } catch (RuntimeException e) {
      throw new NotificationException("El proveedor SMTP rechazó el envío.", e);
    }
  }
}
