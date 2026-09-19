package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.crypto.DataEncryptionService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Pruebas de confirmación por WhatsApp a nivel de servicio (FASE8-05).
 *
 * <p>Habilita el adaptador apuntando a un servidor falso local
 * (vía {@code @DynamicPropertySource}): verifica el envío y el registro como
 * {@code enviada}, y que sin teléfono queda {@code fallida} auditable. La
 * caída del proveedor a nivel de servicio ya está cubierta por el principio
 * nunca-lanza (FASE8-03/04) y a nivel de adaptador en
 * {@code WhatsappSenderIntegrationTest}.
 */
class WhatsappNotificationServiceIntegrationTest extends AbstractIntegrationTest {

  private static HttpServer servidorFalso;
  private static int puertoFalso;
  private static final AtomicReference<String> rutaRecibida = new AtomicReference<>();

  @Autowired
  private NotificationService notificationService;

  @Autowired
  private AppointmentRepository appointmentRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private DataEncryptionService encryptionService;

  private Tenant tenantA;
  private Appointment citaConTelefono;
  private Appointment citaSinTelefono;

  @BeforeAll
  static void iniciarServidorFalso() throws IOException {
    servidorFalso = HttpServer.create(new InetSocketAddress(0), 0);
    servidorFalso.createContext("/", intercambio -> {
      rutaRecibida.set(intercambio.getRequestURI().getPath());
      byte[] bytes = "{\"messages\":[{\"id\":\"wamid.test\"}]}".getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(200, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    servidorFalso.start();
    puertoFalso = servidorFalso.getAddress().getPort();
  }

  @AfterAll
  static void detenerServidorFalso() {
    if (servidorFalso != null) {
      servidorFalso.stop(0);
    }
  }

  @DynamicPropertySource
  static void whatsappFalso(DynamicPropertyRegistry registry) {
    registry.add("odentix.notifications.whatsapp.enabled", () -> "true");
    registry.add("odentix.notifications.whatsapp.api-base-url",
        () -> "http://localhost:" + puertoFalso);
    registry.add("odentix.notifications.whatsapp.phone-number-id", () -> "999888");
    registry.add("odentix.notifications.whatsapp.token", () -> "token-falso");
    // Llave fija de 32 bytes en base64 para cifrar el token propio en tests.
    registry.add("odentix.crypto.key", () ->
        Base64.getEncoder().encodeToString(new byte[32]));
  }

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica WhatsApp Alfa", "9C0111222-1"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient conTelefono = patientRepository.save(new Patient(tenantA.getId(), "Tulio", "Voz"));
      conTelefono.setPhone("573001112233");
      conTelefono = patientRepository.save(conTelefono);
      Patient sinTelefono = patientRepository.save(new Patient(tenantA.getId(), "Muda", "Sil"));
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantA.getId(), "Dr. WAS"));
      Instant inicio = Instant.now().plus(2, ChronoUnit.DAYS);
      Instant fin = inicio.plus(30, ChronoUnit.MINUTES);
      citaConTelefono = appointmentRepository.saveAndFlush(
          new Appointment(tenantA.getId(), conTelefono, profesional, inicio, fin));
      citaSinTelefono = appointmentRepository.saveAndFlush(
          new Appointment(tenantA.getId(), sinTelefono, profesional,
              inicio.plus(1, ChronoUnit.HOURS), fin.plus(1, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void confirmacionPorWhatsAppSeEnviaYRegistra() {
    TenantContext.setTenantId(tenantA.getId());
    Notification intento;
    try {
      intento = notificationService.sendAppointmentWhatsAppConfirmation(citaConTelefono.getId());
    } finally {
      TenantContext.clear();
    }

    assertThat(intento.getStatus()).isEqualTo(NotificationStatus.enviada);
    assertThat(intento.getChannel()).isEqualTo(NotificationChannel.whatsapp);
    assertThat(intento.getRecipient()).isEqualTo("573001112233");
    assertThat(intento.getTemplateKey()).isEqualTo("cita_confirmacion");
    assertThat(intento.getSentAt()).isNotNull();
  }

  @Test
  void pacienteSinTelefonoRegistraFallida() {    TenantContext.setTenantId(tenantA.getId());
    Notification intento;
    try {
      intento = notificationService.sendAppointmentWhatsAppConfirmation(citaSinTelefono.getId());
    } finally {
      TenantContext.clear();
    }

    assertThat(intento.getStatus()).isEqualTo(NotificationStatus.fallida);
    assertThat(intento.getErrorDetail()).contains("teléfono");
  }

  @Test
  void conNumeroPropioEnviaDesdeEseNumero() {
    // La clínica configura su número+token: el envío sale por ahí, no por el global.
    TenantContext.setTenantId(tenantA.getId());
    try {
      Tenant tenant = tenantRepository.findById(tenantA.getId()).orElseThrow();
      tenant.setWhatsappPhoneNumberId("111222333");
      tenant.setWhatsappTokenCifrado(encryptionService.cifrar("token-propio"));
      tenantRepository.saveAndFlush(tenant);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantA.getId());
    try {
      Notification intento =
          notificationService.sendAppointmentWhatsAppConfirmation(citaConTelefono.getId());
      assertThat(intento.getStatus()).isEqualTo(NotificationStatus.enviada);
    } finally {
      TenantContext.clear();
    }

    assertThat(rutaRecibida.get()).contains("111222333");
    assertThat(rutaRecibida.get()).doesNotContain("999888");
  }
}
