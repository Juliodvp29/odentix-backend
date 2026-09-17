package com.julio.odentix.odentix_backend.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para el registro en lista de espera (FASE3-06)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: se puede registrar un paciente en lista de
 * espera para un tipo de procedimiento y rango de fechas.
 */
@AutoConfigureMockMvc
class WaitlistIntegrationTest extends AbstractIntegrationTest {

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

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenEspecialistaA;
  private Patient patientA;
  private Patient patientB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Espera Alfa", "909111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@espera-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@espera-alfa.com", "ClaveSegura789!", "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Espera Beta", "910333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@espera-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Beto", "Pérez"));
    } finally {
      TenantContext.clear();
    }
  }

  private CreateWaitlistEntryRequest solicitud(UUID patientId) {
    Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    return new CreateWaitlistEntryRequest(
        patientId, UUID.randomUUID(), from, from.plus(7, ChronoUnit.DAYS));
  }

  @Test
  void registrarInteresadoDevuelve201EnEstadoActiva() throws Exception {
    UUID procedureId = UUID.randomUUID();
    CreateWaitlistEntryRequest request = solicitud(patientA.getId());
    request.setProcedureId(procedureId);

    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.procedureId").value(procedureId.toString()))
        .andExpect(jsonPath("$.desiredFrom").value(request.getDesiredFrom().toString()))
        .andExpect(jsonPath("$.desiredTo").value(request.getDesiredTo().toString()))
        .andExpect(jsonPath("$.status").value("activa"));
  }

  @Test
  void pacienteDeOtroTenantDevuelve404() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(solicitud(patientB.getId()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void patientIdAusenteDevuelve400() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.patientId").exists());
  }

  @Test
  void rangoInvertidoDevuelve400() throws Exception {
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    CreateWaitlistEntryRequest request =
        new CreateWaitlistEntryRequest(patientA.getId(), null, base.plus(7, ChronoUnit.DAYS), base);

    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void rolNoAutorizadoDevuelve403() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(solicitud(patientA.getId()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(solicitud(patientA.getId()))))
        .andExpect(status().isUnauthorized());
  }
}
