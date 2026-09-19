package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Adaptador de email vía SMTP (FASE8-03, remitente por clínica pre-Fase 12).
 *
 * <p>Se activa con {@code odentix.notifications.email.enabled=true}; la conexión
 * sale de variables de entorno (`spring.mail.*`) — nunca hay credenciales en el
 * repo (regla §8 de AGENTS.md). Los timeouts SMTP son cortos (ver
 * `application.yml`): un proveedor caído falla rápido y el servicio lo
 * registra como intento `fallida` sin bloquear el flujo de negocio.
 *
 * <p><b>Remitente por clínica:</b> el `From` sale de
 * `tenants.notification_email`; sin valor se usa el global
 * `odentix.notifications.email.from`, y en ese caso el `Reply-To` apunta al
 * correo de la clínica para que el "responder" llegue a quien debe. Nota de
 * proveedor: el `From` variable exige un SMTP que permita múltiples
 * remitentes verificados (Resend/SES/SendGrid) — Gmail solo deja sus alias.
 */
@Component
@ConditionalOnProperty(
    prefix = "odentix.notifications.email",
    name = "enabled",
    havingValue = "true")
public class SmtpEmailNotificationSender implements NotificationSender {

  private final JavaMailSender mailSender;
  private final TenantRepository tenantRepository;
  private final String globalFrom;

  public SmtpEmailNotificationSender(
      JavaMailSender mailSender,
      TenantRepository tenantRepository,
      @Value("${odentix.notifications.email.from:}") String from) {
    this.mailSender = mailSender;
    this.tenantRepository = tenantRepository;
    this.globalFrom = from != null ? from.strip() : "";
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.email;
  }

  @Override
  public void send(String recipient, String subject, String body) throws NotificationException {
    String tenantEmail = correoDeLaClinica();
    String from = !tenantEmail.isEmpty() ? tenantEmail : globalFrom;
    if (from.isEmpty()) {
      throw new NotificationException(
          "Falta configurar el remitente: ni la clínica tiene notification_email "
              + "ni existe odentix.notifications.email.from (NOTIFICATIONS_EMAIL_FROM).");
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(from);
      // El responder siempre va a la clínica.
      if (!tenantEmail.isEmpty()) {
        message.setReplyTo(tenantEmail);
      }
      message.setTo(recipient);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
    } catch (RuntimeException e) {
      throw new NotificationException("El proveedor SMTP rechazó el envío.", e);
    }
  }

  private String correoDeLaClinica() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      return "";
    }
    return tenantRepository.findById(tenantId)
        .map(t -> t.getNotificationEmail() != null ? t.getNotificationEmail().strip() : "")
        .orElse("");
  }
}
