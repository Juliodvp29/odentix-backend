package com.julio.odentix.odentix_backend.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para el cambio de estado de citas (FASE3-04)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: las transiciones inválidas devuelven 400 con
 * un mensaje claro sobre qué se intentó y por qué no es válido.
 */
@AutoConfigureMockMvc
class AppointmentStatusIntegrationTest extends AbstractIntegrationTest {

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

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private String tokenEspecialistaA;
  private Patient patientA;
  private Patient patientB;
  private Professional professionalA;
  private Professional professionalB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Estados Alfa", "905111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@estados-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@estados-alfa.com", "ClaveSegura789!", "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Estados Beta", "906333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@estados-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. House"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Beto", "Pérez"));
      professionalB = professionalRepository.save(new Professional(tenantB.getId(), "Dr. Extraño"));
    } finally {
      TenantContext.clear();
    }
  }

  private String crearCita(String token, UUID patientId, UUID professionalId) throws Exception {
    Instant startsAt = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    CreateAppointmentRequest request =
        new CreateAppointmentRequest(patientId, professionalId, startsAt, startsAt.plus(1, ChronoUnit.HOURS));
    MvcResult result = mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return body.get("id").toString();
  }

  private void cambiarEstado(String token, String appointmentId, AppointmentStatus estado, int esperado)
      throws Exception {
    mockMvc.perform(patch("/api/v1/appointments/" + appointmentId + "/status")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateAppointmentStatusRequest(estado))))
        .andExpect(status().is(esperado));
  }

  @Test
  void cadenaFelizProgramadaConfirmadaAtendida() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(citaId))
        .andExpect(jsonPath("$.status").value("confirmada"));

    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.atendida))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("atendida"));
  }

  @Test
  void programadaPuedeCancelarseYCanceladaEsFinal() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    cambiarEstado(tokenA, citaId, AppointmentStatus.cancelada, 200);

    // De cancelada no se sale hacia ningún estado.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.atendida))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.message").value(Matchers.allOf(
            Matchers.containsString("cancelada"),
            Matchers.containsString("atendida"),
            Matchers.containsString("estado final"))));
  }

  @Test
  void saltarConfirmacionYRetrocederDevuelven400() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    // programada → atendida salta la confirmación.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.atendida))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(Matchers.allOf(
            Matchers.containsString("programada"),
            Matchers.containsString("atendida"))));

    cambiarEstado(tokenA, citaId, AppointmentStatus.confirmada, 200);
    cambiarEstado(tokenA, citaId, AppointmentStatus.atendida, 200);

    // atendida → confirmada es un retroceso.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(Matchers.allOf(
            Matchers.containsString("atendida"),
            Matchers.containsString("confirmada"))));
  }

  @Test
  void mismoEstadoEsIdempotente() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    cambiarEstado(tokenA, citaId, AppointmentStatus.confirmada, 200);

    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("confirmada"));
  }

  @Test
  void estadoInvalidoDevuelve400() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    // Enum inexistente → 400 por el handler de JSON malformado, no 500.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"inexistente\"}"))
        .andExpect(status().isBadRequest());

    // Estado ausente → 400 de validación con el campo.
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.status").exists());
  }

  @Test
  void crossTenantDevuelve404YNoAlteraLaCita() throws Exception {
    String citaIdB = crearCita(tokenB, patientB.getId(), professionalB.getId());

    mockMvc.perform(patch("/api/v1/appointments/" + citaIdB + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.cancelada))))
        .andExpect(status().isNotFound());

    // La cita del tenant B sigue programada.
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenB)
            .param("from", base.toString())
            .param("to", base.plus(2, ChronoUnit.DAYS).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("programada"));
  }

  @Test
  void rolNoAutorizadoDevuelve403() throws Exception {
    String citaId = crearCita(tokenA, patientA.getId(), professionalA.getId());

    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isForbidden());
  }
}
