package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.notification.sender.NotificationException;
import com.julio.odentix.odentix_backend.notification.sender.SmtpEmailNotificationSender;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Pruebas del remitente por clínica en SMTP (pre-Fase 12).
 *
 * <p>El adaptador solo vive con `enabled=true`, así que se instancia a mano con
 * `JavaMailSender` mockeado y `TenantRepository` real: From de la clínica,
 * fallback al global con Reply-To, y error claro sin ninguno.
 */
class SmtpSenderIntegrationTest extends AbstractIntegrationTest {

  @MockitoBean
  private JavaMailSender mailSender;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant conCorreo;
  private Tenant sinCorreo;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    conCorreo = tenantRepository.save(new Tenant("Clínica Remitente", "9P0111222-1"));
    conCorreo.setNotificationEmail("citas@clinica-remitente.com");
    conCorreo.setNotificationName("Clínica Remitente");
    conCorreo = tenantRepository.save(conCorreo);

    sinCorreo = tenantRepository.save(new Tenant("Clínica Sin Correo", "9P0133444-2"));
  }

  private SmtpEmailNotificationSender sender(String globalFrom) {
    return new SmtpEmailNotificationSender(mailSender, tenantRepository, globalFrom);
  }

  private SimpleMailMessage enviado() {
    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captor.capture());
    return captor.getValue();
  }

  private void como(UUID tenantId, Runnable accion) {
    TenantContext.setTenantId(tenantId);
    try {
      accion.run();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void usaCorreoDeLaClinica() {
    como(conCorreo.getId(),
        () -> sender("global@odentix.com").send("paciente@x.com", "Asunto", "Cuerpo"));

    SimpleMailMessage mensaje = enviado();
    assertThat(mensaje.getFrom()).isEqualTo("citas@clinica-remitente.com");
    assertThat(mensaje.getReplyTo()).isEqualTo("citas@clinica-remitente.com");
    assertThat(mensaje.getTo()).containsExactly("paciente@x.com");
  }

  @Test
  void sinCorreoUsaGlobalSinReplyTo() {
    como(sinCorreo.getId(),
        () -> sender("global@odentix.com").send("paciente@x.com", "Asunto", "Cuerpo"));

    SimpleMailMessage mensaje = enviado();
    assertThat(mensaje.getFrom()).isEqualTo("global@odentix.com");
    assertThat(mensaje.getReplyTo()).isNull();
  }

  @Test
  void sinNingunoFallaClaro() {
    como(sinCorreo.getId(), () -> assertThatThrownBy(
            () -> sender("").send("paciente@x.com", "Asunto", "Cuerpo"))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("remitente"));
  }
}
