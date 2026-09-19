package com.julio.odentix.odentix_backend.notification.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.notification.sender.NotificationException;
import com.julio.odentix.odentix_backend.notification.sender.NotificationSender;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.subscription.service.SubscriptionService;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
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

  private final List<NotificationSender> notificationSenders;
  private final NotificationRepository notificationRepository;
  private final AppointmentRepository appointmentRepository;
  private final SubscriptionService subscriptionService;

  public NotificationService(
      List<NotificationSender> notificationSenders,
      NotificationRepository notificationRepository,
      AppointmentRepository appointmentRepository,
      SubscriptionService subscriptionService) {
    this.notificationSenders = List.copyOf(notificationSenders);
    this.notificationRepository = notificationRepository;
    this.appointmentRepository = appointmentRepository;
    this.subscriptionService = subscriptionService;
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
      Notification intento = nuevoIntento(tenantId,
          cita.getPatient() != null ? cita.getPatient().getId() : null, null,
          NotificationChannel.email, "");
      intento.setTemplateKey(TEMPLATE_CONFIRMACION_CITA);
      return registrarFallida(intento, "El paciente no tiene email registrado.");
    }

    String nombre = cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName();
    String asunto = "Confirmación de tu cita odontológica";
    String cuerpo = "Hola " + nombre.strip() + ", te confirmamos tu cita del "
        + cita.getStartsAt() + ". Por favor llega 10 minutos antes. ¡Te esperamos!";

    return enviar(cita, NotificationChannel.email,
        email.strip(), TEMPLATE_CONFIRMACION_CITA, asunto, cuerpo);
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
      Notification intento = nuevoIntento(tenantId,
          cita.getPatient() != null ? cita.getPatient().getId() : null, null,
          NotificationChannel.email, "");
      intento.setTemplateKey(TEMPLATE_CITA_AGENDADA);
      return registrarFallida(intento, "El paciente no tiene email registrado.");
    }

    String nombre = cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName();
    String asunto = "Tu cita odontológica quedó agendada";
    String cuerpo = "Hola " + nombre.strip() + ", tu cita quedó agendada para el "
        + cita.getStartsAt() + ". Te contactaremos para confirmar tu asistencia.";

    return enviar(cita, NotificationChannel.email,
        email.strip(), TEMPLATE_CITA_AGENDADA, asunto, cuerpo);
  }

  /**
   * Envía la confirmación de una cita por WhatsApp y registra el intento (FASE8-05).
   *
   * <p>Destinatario = teléfono del paciente. Si no hay teléfono o no hay
   * adaptador de WhatsApp configurado, queda un intento {@code fallida}
   * auditable. Misma garantía que el email: nunca lanza.
   *
   * @param appointmentId cita dentro del tenant activo.
   * @return intento registrado, con su estado final.
   */
  @Transactional
  public Notification sendAppointmentWhatsAppConfirmation(UUID appointmentId) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Appointment cita = appointmentRepository.findByIdAndTenantId(appointmentId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cita no encontrada: " + appointmentId));

    String telefono = cita.getPatient() != null ? cita.getPatient().getPhone() : null;
    if (telefono == null || telefono.isBlank()) {
      Notification intento = nuevoIntento(tenantId,
          cita.getPatient() != null ? cita.getPatient().getId() : null, null,
          NotificationChannel.whatsapp, "");
      intento.setTemplateKey(TEMPLATE_CONFIRMACION_CITA);
      return registrarFallida(intento, "El paciente no tiene teléfono registrado.");
    }

    String nombre = cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName();
    String cuerpo = "Hola " + nombre.strip() + ", te confirmamos tu cita odontológica del "
        + cita.getStartsAt() + ". Por favor llega 10 minutos antes. ¡Te esperamos!";

    // WhatsApp no tiene asunto: se envía solo el cuerpo.
    return enviar(cita, NotificationChannel.whatsapp,
        telefono.strip(), TEMPLATE_CONFIRMACION_CITA, "", cuerpo);
  }

  /**
   * Envía un mensaje libre por un canal y registra el intento (FASE9-03).
   *
   * <p>Lo usa la ejecución de acciones de oportunidad: el mensaje sugerido se
   * congela al detectar, pero el envío y el registro pasan por aquí con la
   * misma garantía de nunca lanzar.
   */
  @Transactional
  public Notification sendCustomMessage(
      UUID tenantId,
      NotificationChannel channel,
      String destinatario,
      String asunto,
      String cuerpo,
      String templateKey,
      UUID patientId,
      Map<String, String> payload) {
    return enviarGenerico(tenantId, patientId, payload, channel,
        destinatario, templateKey, asunto, cuerpo);
  }

  /**
   * Núcleo común de envío: construye el intento, lo envía por el adaptador del
   * canal y registra el resultado. Nunca lanza (ver garantía de la clase).
   */
  private Notification enviar(
      Appointment cita, NotificationChannel channel, String destinatario,
      String templateKey, String asunto, String cuerpo) {
    return enviarGenerico(
        cita.getTenantId(),
        cita.getPatient() != null ? cita.getPatient().getId() : null,
        Map.of(
            "appointmentId", cita.getId().toString(),
            "startsAt", cita.getStartsAt().toString()),
        channel, destinatario, templateKey, asunto, cuerpo);
  }

  private Notification enviarGenerico(
      UUID tenantId, UUID patientId, Map<String, String> payload,
      NotificationChannel channel, String destinatario,
      String templateKey, String asunto, String cuerpo) {
    // FASE11-03: la cuota mensual de WhatsApp se verifica antes de enviar.
    // Si está agotada lanza 429 (no se registra intento: no hubo consumo).
    // El email no tiene cuota y pasa directo.
    if (channel == NotificationChannel.whatsapp) {
      subscriptionService.checkWhatsAppQuota(tenantId);
    }
    Notification intento = nuevoIntento(tenantId, patientId, payload, channel, destinatario);
    intento.setTemplateKey(templateKey);

    NotificationSender sender;
    try {
      sender = senderPara(channel);
    } catch (NotificationException e) {
      return registrarFallida(intento, e.getMessage());
    }
    try {
      sender.send(intento.getRecipient(), asunto, cuerpo);
    } catch (RuntimeException e) {
      // NotificationException y cualquier fallo inesperado del adaptador.
      return registrarFallida(intento, mensajeFallo(e));
    }

    intento.setStatus(NotificationStatus.enviada);
    intento.setSentAt(Instant.now());
    return notificationRepository.save(intento);
  }

  private NotificationSender senderPara(NotificationChannel channel) {
    return notificationSenders.stream()
        .filter(sender -> sender.channel() == channel)
        .findFirst()
        .orElseThrow(() -> new NotificationException(
            "Sin adaptador configurado para el canal " + channel + "."));
  }

  private Notification nuevoIntento(
      UUID tenantId, UUID patientId, Map<String, String> payload,
      NotificationChannel channel, String destinatario) {
    Notification intento = new Notification(tenantId, channel, destinatario);
    intento.setPatientId(patientId);
    intento.setPayload(payload != null ? new java.util.HashMap<>(payload) : null);
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
