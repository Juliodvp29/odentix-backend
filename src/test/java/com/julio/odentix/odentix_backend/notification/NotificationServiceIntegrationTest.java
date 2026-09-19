package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.notification.sender.NotificationException;
import com.julio.odentix.odentix_backend.notification.sender.NotificationSender;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración para {@link NotificationService} (FASE8-03) contra
 * PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Disparar una confirmación de cita por email la envía y la deja
 *       registrada como {@code enviada} con su resultado.</li>
 *   <li>Un fallo del adaptador se registra como {@code fallida} sin propagar
 *       la excepción (principio de resiliencia para FASE8-04).</li>
 *   <li>Paciente sin email y cita de otro tenant: casos borde auditables.</li>
 * </ul>
 */
class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private NotificationSender notificationSender;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private AppointmentRepository appointmentRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Appointment citaA;
  private Appointment citaSinEmailA;
  private Appointment citaB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Notify Alfa", "990111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Notify Beta", "991333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient conEmail = patientRepository.save(new Patient(tenantA.getId(), "Marta", "Gil"));
      conEmail.setEmail("marta.gil@example.com");
      conEmail = patientRepository.save(conEmail);
      Patient sinEmail = patientRepository.save(new Patient(tenantA.getId(), "Pedro", "Sol"));
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantA.getId(), "Dr. Notify"));
      Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS);
      Instant fin = inicio.plus(30, ChronoUnit.MINUTES);
      citaA = appointmentRepository.saveAndFlush(
          new Appointment(tenantA.getId(), conEmail, profesional, inicio, fin));
      citaSinEmailA = appointmentRepository.saveAndFlush(
          new Appointment(tenantA.getId(), sinEmail, profesional,
              inicio.plus(1, ChronoUnit.HOURS), fin.plus(1, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient pacienteB = patientRepository.save(new Patient(tenantB.getId(), "Luz", "Mar"));
      pacienteB.setEmail("luz.mar@example.com");
      pacienteB = patientRepository.save(pacienteB);
      Professional profesionalB =
          professionalRepository.saveAndFlush(new Professional(tenantB.getId(), "Dra. Notify B"));
      Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS);
      citaB = appointmentRepository.saveAndFlush(
          new Appointment(tenantB.getId(), pacienteB, profesionalB,
              inicio, inicio.plus(30, ChronoUnit.MINUTES)));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void confirmacionEnviadaQuedaRegistrada() {
    // Adaptador real activo en tests: el de registro en log (SMTP apagado).
    assertThat(notificationSender.channel()).isEqualTo(NotificationChannel.email);

    TenantContext.setTenantId(tenantA.getId());
    Notification intento;
    try {
      intento = notificationService.sendAppointmentConfirmation(citaA.getId());
    } finally {
      TenantContext.clear();
    }

    assertThat(intento.getId()).isNotNull();
    assertThat(intento.getStatus()).isEqualTo(NotificationStatus.enviada);
    assertThat(intento.getSentAt()).isNotNull();
    assertThat(intento.getErrorDetail()).isNull();
    assertThat(intento.getChannel()).isEqualTo(NotificationChannel.email);
    assertThat(intento.getRecipient()).isEqualTo("marta.gil@example.com");
    assertThat(intento.getTemplateKey()).isEqualTo("cita_confirmacion");
    assertThat(intento.getPatientId()).isNotNull();
    assertThat(intento.getPayload()).containsEntry("appointmentId", citaA.getId().toString());
  }

  @Test
  @Transactional
  void falloDelAdaptadorSeRegistraSinPropagar() {
    // @Transactional: el servicio construido a mano no pasa por el proxy de
    // Spring, así que la sesión para el paciente lazy la abre el propio test.
    // Adaptador roto construido a mano: el servicio es un POJO y recibe el
    // sender por constructor, sin necesidad de mocks del contexto.
    NotificationSender roto = new NotificationSender() {
      @Override
      public NotificationChannel channel() {
        return NotificationChannel.email;
      }

      @Override
      public void send(String recipient, String subject, String body) {
        throw new NotificationException("SMTP simulado caído.");
      }
    };
    NotificationService servicioSinProveedor =
        new NotificationService(roto, notificationRepository, appointmentRepository);

    TenantContext.setTenantId(tenantA.getId());
    Notification intento;
    try {
      // No lanza: el fallo queda registrado.
      intento = servicioSinProveedor.sendAppointmentConfirmation(citaA.getId());
    } finally {
      TenantContext.clear();
    }

    assertThat(intento.getStatus()).isEqualTo(NotificationStatus.fallida);
    assertThat(intento.getSentAt()).isNull();
    assertThat(intento.getErrorDetail()).contains("SMTP simulado caído");
  }

  @Test
  void pacienteSinEmailRegistraFallidaAuditable() {
    TenantContext.setTenantId(tenantA.getId());
    Notification intento;
    try {
      intento = notificationService.sendAppointmentConfirmation(citaSinEmailA.getId());
    } finally {
      TenantContext.clear();
    }

    assertThat(intento.getStatus()).isEqualTo(NotificationStatus.fallida);
    assertThat(intento.getErrorDetail()).contains("no tiene email");
  }

  @Test
  void citaDeOtroTenantNoExiste() {
    // Autenticado como A, la cita de B es invisible (404, no 403).
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(() -> notificationService.sendAppointmentConfirmation(citaB.getId()))
          .isInstanceOf(ResourceNotFoundException.class);
    } finally {
      TenantContext.clear();
    }
  }
}
