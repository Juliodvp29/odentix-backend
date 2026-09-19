package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateAppointmentStatusRequest;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.notification.sender.NotificationException;
import com.julio.odentix.odentix_backend.notification.sender.NotificationSender;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prueba de resiliencia del disparo de notificaciones (FASE8-04): con el
 * proveedor caído, crear y confirmar citas responden con éxito igual y los
 * fallos quedan registrados como {@code fallida} sin revertir la operación
 * de negocio.
 */
@AutoConfigureMockMvc
class AppointmentNotificationResilienceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @MockitoBean
  private NotificationSender notificationSender;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private String tokenA;
  private Patient patientA;
  private Professional professionalA;

  @BeforeEach
  void setUp() {
    // Proveedor caído para toda la clase. El channel() también se stubéa:
    // el servicio despacha por canal y un mock sin stub devuelve null.
    doReturn(NotificationChannel.email).when(notificationSender).channel();
    doThrow(new NotificationException("Proveedor simulado caído."))
        .when(notificationSender).send(anyString(), anyString(), anyString());

    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Notify Caída Alfa", "9B0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@caida-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Omar", "Roca"));
      patientA.setEmail("omar.roca@example.com");
      patientA = patientRepository.save(patientA);
      professionalA =
          professionalRepository.saveAndFlush(new Professional(tenantA.getId(), "Dr. Caída"));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void falloDelProveedorNoBloqueaCrearNiConfirmar() throws Exception {
    Instant startsAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    CreateAppointmentRequest request = new CreateAppointmentRequest(
        patientA.getId(), professionalA.getId(), startsAt, startsAt.plus(1, ChronoUnit.HOURS));

    // Crear responde 201 aunque el aviso falle.
    MvcResult creada = mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> body = objectMapper.readValue(creada.getResponse().getContentAsString(), Map.class);
    String citaId = body.get("id").toString();

    // Confirmar responde 200 aunque la confirmación falle.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isOk());

    // Ambos intentos quedaron registrados como fallida con su detalle.
    TenantContext.setTenantId(tenantA.getId());
    List<Notification> intentos;
    try {
      intentos = notificationRepository.findAll();
    } finally {
      TenantContext.clear();
    }
    assertThat(intentos).hasSize(2);
    assertThat(intentos).extracting(Notification::getTemplateKey)
        .containsExactlyInAnyOrder("cita_agendada", "cita_confirmacion");
    assertThat(intentos).extracting(Notification::getStatus)
        .containsOnly(NotificationStatus.fallida);
    assertThat(intentos).extracting(Notification::getErrorDetail)
        .allSatisfy(detalle -> assertThat(detalle).contains("simulado caído"));
  }
}
