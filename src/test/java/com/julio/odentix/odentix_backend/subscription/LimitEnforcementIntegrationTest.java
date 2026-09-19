package com.julio.odentix.odentix_backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.exception.LimitExceededException;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de límites numéricos por plan (FASE11-03) contra PostgreSQL real vía
 * Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Esencial con 150 pacientes activos → el 151 vía API responde 429 con
 *       mensaje de plan/límite (y sin `Retry-After`, que no aplica a topes).</li>
 *   <li>Esencial con 2 usuarios → el 3º a nivel de servicio lanza
 *       `LimitExceededException` (sin endpoint de usuarios).</li>
 *   <li>WhatsApp Esencial (cuota 0) → 429 con `Retry-After` al primer envío;
 *       Profesional con consumo bajo → pasa; Clínica (NULL) → pasa siempre.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class LimitEnforcementIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PlanRepository planRepository;

  @Autowired
  private TenantSubscriptionRepository subscriptionRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private NotificationService notificationService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant esencial;
  private Tenant profesional;
  private Tenant clinica;
  private String tokenEsencial;
  private String tokenClinica;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    esencial = suscribir("Clínica Límite Esencial", "9L0111222-1",
        "propietario@limite-esencial.com", "esencial");
    tokenEsencial = generarToken(esencial.getId(), "propietario@limite-esencial.com");
    profesional = suscribir("Clínica Límite Profesional", "9L0133444-2",
        "propietario@limite-profesional.com", "profesional");
    clinica = suscribir("Clínica Límite Clínica", "9L0155666-3",
        "propietario@limite-clinica.com", "clinica");
    tokenClinica = generarToken(clinica.getId(), "propietario@limite-clinica.com");
  }

  private Tenant suscribir(String nombre, String nit, String email, String planCode) {
    Tenant tenant = tenantRepository.save(new Tenant(nombre, nit));
    userService.createUser(tenant.getId(), email, "ClaveSegura123!", "Propietario",
        UserRole.propietario);
    Plan plan = planRepository.findByCode(planCode).orElseThrow();
    TenantContext.setTenantId(tenant.getId());
    try {
      subscriptionRepository.saveAndFlush(
          new TenantSubscription(tenant.getId(), plan, Instant.now().plusSeconds(2_592_000)));
    } finally {
      TenantContext.clear();
    }
    return tenant;
  }

  private String generarToken(UUID tenantId, String email) {
    return jwtService.generateToken(
        userService.createUser(tenantId, "tok-" + email, "ClaveSegura123!", "Token",
            UserRole.propietario));
  }

  private void sembrarPacientes(UUID tenantId, int cantidad) {
    TenantContext.setTenantId(tenantId);
    try {
      for (int i = 0; i < cantidad; i++) {
        patientRepository.save(new Patient(tenantId, "Paciente" + i, "Límite"));
      }
    } finally {
      TenantContext.clear();
    }
  }

  private void sembrarEnviadas(UUID tenantId, int cantidad) {
    TenantContext.setTenantId(tenantId);
    try {
      for (int i = 0; i < cantidad; i++) {
        Notification intento =
            new Notification(tenantId, NotificationChannel.whatsapp, "57300" + i);
        intento.setStatus(NotificationStatus.enviada);
        notificationRepository.save(intento);
      }
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE11-03)
  // ---------------------------------------------------------------------------

  @Test
  void paciente151EnEsencialDevuelve429() throws Exception {
    sembrarPacientes(esencial.getId(), 150);

    Map<String, Object> body = Map.of("firstName", "Extra", "lastName", "Limite");
    MvcResult result = mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenEsencial)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().doesNotExist("Retry-After"))
        .andReturn();
    assertThat(result.getResponse().getContentAsString()).contains("150");
  }

  @Test
  void tercerUsuarioEnEsencialLanzaLimite() {
    // Esencial ya trae 2 usuarios (propietario + token): el siguiente excede.
    assertThatThrownBy(() -> userService.createUser(
            esencial.getId(), "tercero@limite-esencial.com", "ClaveSegura123!", "Tercero",
            UserRole.recepcion))
        .isInstanceOf(LimitExceededException.class)
        .hasMessageContaining("2");
  }

  @Test
  void whatsappEsencialBloqueadoConRetryAfter() {
    // Cuota 0: el primer envío ya es 429 con segundos al fin del periodo.
    TenantContext.setTenantId(esencial.getId());
    try {
      assertThatThrownBy(() -> notificationService.sendCustomMessage(
              esencial.getId(), NotificationChannel.whatsapp, "573001112233", "", "hola",
              "test", null, Map.of()))
          .isInstanceOf(LimitExceededException.class)
          .satisfies(e -> assertThat(((LimitExceededException) e).getRetryAfterSeconds())
              .isNotNull());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void whatsappProfesionalCuentaSoloEnviadas() {
    // 1 fallida no consume; 2 enviadas sí. El siguiente envío pasa (3/300).
    sembrarEnviadas(profesional.getId(), 2);
    TenantContext.setTenantId(profesional.getId());
    try {
      Notification fallida = new Notification(
          profesional.getId(), NotificationChannel.whatsapp, "573009998877");
      fallida.setStatus(NotificationStatus.fallida);
      notificationRepository.saveAndFlush(fallida);

      Notification intento = notificationService.sendCustomMessage(
          profesional.getId(), NotificationChannel.whatsapp, "573001112233", "", "hola",
          "test", null, Map.of());
      assertThat(intento.getStatus()).isEqualTo(NotificationStatus.fallida);
      // Sin adaptador WhatsApp en tests: fallida por configuración, no por cuota.
      assertThat(intento.getErrorDetail()).contains("adaptador");

      long enviadas = notificationRepository
          .countByTenantIdAndChannelAndStatusAndCreatedAtGreaterThanEqual(
              profesional.getId(), NotificationChannel.whatsapp, NotificationStatus.enviada,
              Instant.now().minusSeconds(3600));
      assertThat(enviadas).isEqualTo(2L);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void clinicaIlimitadaPasaSiempre() throws Exception {
    // max_patients/max_users NULL: crear paciente vía API responde 201.
    Map<String, Object> body = Map.of("firstName", "Libre", "lastName", "Clinica");
    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenClinica)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated());

    // WhatsApp Clínica (1000): sin adaptador en tests queda fallida, pero pasa
    // la cuota (no lanza LimitExceededException).
    TenantContext.setTenantId(clinica.getId());
    try {
      Notification intento = notificationService.sendCustomMessage(
          clinica.getId(), NotificationChannel.whatsapp, "573001112233", "", "hola",
          "test", null, Map.of());
      assertThat(intento.getStatus()).isEqualTo(NotificationStatus.fallida);
    } finally {
      TenantContext.clear();
    }
  }
}
