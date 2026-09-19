package com.julio.odentix.odentix_backend.notification.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.notification.sender.NotificationSender;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de notificaciones desacoplado por canal (FASE8-03).
 *
 * <p>Principio de resiliencia (roadmap §8/§10): <b>este servicio nunca lanza</b>.
 * Todo fallo del proveedor —timeout, rechazo, mala configuración— se captura y
 * se persiste como intento {@code fallida} con su {@code error_detail}. Así el
 * flujo de negocio que originó el envío (confirmar cita en FASE8-04, WhatsApp
 * en FASE8-05) nunca se bloquea por un canal caído.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: una cita de otro tenant
 * resulta invisible (404), conforme a la regla §5 de AGENTS.md.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private static final String TEMPLATE_CONFIRMACION_CITA = "cita_confirmacion";
  private static final String TEMPLATE_CITA_AGENDADA = "cita_agendada";

  private final NotificationSender notificationSender;
  private final NotificationRepository notificationRepository;
  private final AppointmentRepository appointmentRepository;

  public NotificationService(
      NotificationSender notificationSender,
      NotificationRepository notificationRepository,
      AppointmentRepository appointmentRepository) {
    this.notificationSender = notificationSender;
    this.notificationRepository = notificationRepository;
    this.appointmentRepository = appointmentRepository;
  }

  /**
   * Envía la confirmación de una cita por email y registra el intento.
   *
   * <p>Si el paciente no tiene email registrado, no se intenta el envío: queda
   * un intento {@code fallida} auditable ("sin email") en vez de una excepción.
   *
   * @param appointmentId cita dentro del tenant activo.
   * @return intento registrado, con su estado final.
   */
  @Transactional
  public Notification sendAppointmentConfirmation(UUID appointmentId) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Appointment cita = appointmentRepository.findByIdAndTenantId(appointmentId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cita no encontrada: " + appointmentId));

    String email = cita.getPatient() != null ? cita.getPatient().getEmail() : null;
    if (email == null || email.isBlank()) {
      Notification intento = nuevoIntento(tenantId, cita, "");
      intento.setTemplateKey(TEMPLATE_CONFIRMACION_CITA);
      return registrarFallida(intento, "El paciente no tiene email registrado.");
    }

    String nombre = cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName();
    String asunto = "Confirmación de tu cita odontológica";
    String cuerpo = "Hola " + nombre.strip() + ", te confirmamos tu cita del "
        + cita.getStartsAt() + ". Por favor llega 10 minutos antes. ¡Te esperamos!";

    return enviar(cita, email.strip(), TEMPLATE_CONFIRMACION_CITA, asunto, cuerpo);
  }

  /**
   * Envía el aviso de cita agendada por email y registra el intento.
   *
   * <p>Se dispara al crear la cita (FASE8-04): confirma que quedó agendada y
   * anticipa que se pedirá confirmación cuando se acerque la fecha (regla de
   * FASE8-02). Misma garantía que la confirmación: nunca lanza.
   *
   * @param appointmentId cita dentro del tenant activo.
   * @return intento registrado, con su estado final.
   */
  @Transactional
  public Notification sendAppointmentScheduled(UUID appointmentId) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Appointment cita = appointmentRepository.findByIdAndTenantId(appointmentId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cita no encontrada: " + appointmentId));

    String email = cita.getPatient() != null ? cita.getPatient().getEmail() : null;
    if (email == null || email.isBlank()) {
      Notification intento = nuevoIntento(tenantId, cita, "");
      intento.setTemplateKey(TEMPLATE_CITA_AGENDADA);
      return registrarFallida(intento, "El paciente no tiene email registrado.");
    }

    String nombre = cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName();
    String asunto = "Tu cita odontológica quedó agendada";
    String cuerpo = "Hola " + nombre.strip() + ", tu cita quedó agendada para el "
        + cita.getStartsAt() + ". Te contactaremos para confirmar tu asistencia.";

    return enviar(cita, email.strip(), TEMPLATE_CITA_AGENDADA, asunto, cuerpo);
  }

  /**
   * Núcleo común de envío: construye el intento, lo envía y registra el
   * resultado. Nunca lanza (ver garantía de la clase).
   */
  private Notification enviar(
      Appointment cita, String destinatario, String templateKey, String asunto, String cuerpo) {
    Notification intento = nuevoIntento(cita.getTenantId(), cita, destinatario);
    intento.setTemplateKey(templateKey);

    try {
      notificationSender.send(intento.getRecipient(), asunto, cuerpo);
    } catch (RuntimeException e) {
      // NotificationException y cualquier fallo inesperado del adaptador.
      return registrarFallida(intento, mensajeFallo(e));
    }

    intento.setStatus(NotificationStatus.enviada);
    intento.setSentAt(Instant.now());
    return notificationRepository.save(intento);
  }

  private Notification nuevoIntento(UUID tenantId, Appointment cita, String destinatario) {
    Notification intento = new Notification(tenantId, NotificationChannel.email, destinatario);
    intento.setPatientId(cita.getPatient() != null ? cita.getPatient().getId() : null);
    intento.setPayload(Map.of(
        "appointmentId", cita.getId().toString(),
        "startsAt", cita.getStartsAt().toString()));
    return intento;
  }

  private Notification registrarFallida(Notification intento, String detalle) {
    intento.setStatus(NotificationStatus.fallida);
    intento.setErrorDetail(detalle);
    log.warn("Notificación fallida (canal={}): {}", intento.getChannel(), detalle);
    return notificationRepository.save(intento);
  }

  private static String mensajeFallo(Exception e) {
    String mensaje = e.getMessage();
    if (mensaje == null || mensaje.isBlank()) {
      return "El proveedor de envío falló sin detalle (" + e.getClass().getSimpleName() + ").";
    }
    // El detalle va a la BD (auditable), nunca al cliente (GlobalExceptionHandler
    // no expone error_detail; los endpoints futuros solo muestran el estado).
    return mensaje.length() > 500 ? mensaje.substring(0, 500) : mensaje;
  }
}
