package com.julio.odentix.odentix_backend.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la consulta de agenda (FASE3-03) contra
 * PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Filtrar por rango de fechas devuelve solo las citas de ese rango,
 *       ordenadas por hora de inicio.</li>
 *   <li>Filtro opcional por profesional.</li>
 *   <li>Aislamiento cross-tenant (las citas de otro tenant son invisibles).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class AppointmentAgendaIntegrationTest extends AbstractIntegrationTest {

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
  private Professional professionalA1;
  private Professional professionalA2;
  private Professional professionalB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Agenda Alfa", "903111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@agenda-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@agenda-alfa.com", "ClaveSegura789!", "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Agenda Beta", "904333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@agenda-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
      professionalA1 = professionalRepository.save(new Professional(tenantA.getId(), "Dr. House"));
      professionalA2 = professionalRepository.save(new Professional(tenantA.getId(), "Dra. Grey"));
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

  private void crearCita(String token, UUID patientId, UUID professionalId, Instant startsAt, Instant endsAt)
      throws Exception {
    CreateAppointmentRequest request =
        new CreateAppointmentRequest(patientId, professionalId, startsAt, endsAt);
    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  private Instant base() {
    return Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
  }

  @Test
  void filtrarPorRangoDevuelveSoloCitasIncluidasOrdenadas() throws Exception {
    Instant base = base();
    crearCita(tokenA, patientA.getId(), professionalA1.getId(), base, base.plus(1, ChronoUnit.HOURS));
    crearCita(tokenA, patientA.getId(), professionalA2.getId(),
        base.plus(2, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS));
    crearCita(tokenA, patientA.getId(), professionalA1.getId(),
        base.plus(26, ChronoUnit.HOURS), base.plus(27, ChronoUnit.HOURS));

    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].startsAt").value(base.toString()))
        .andExpect(jsonPath("$[0].professionalId").value(professionalA1.getId().toString()))
        .andExpect(jsonPath("$[1].startsAt").value(base.plus(2, ChronoUnit.HOURS).toString()))
        .andExpect(jsonPath("$[1].professionalId").value(professionalA2.getId().toString()));
  }

  @Test
  void filtrarPorProfesionalDevuelveSoloSusCitas() throws Exception {
    Instant base = base();
    crearCita(tokenA, patientA.getId(), professionalA1.getId(), base, base.plus(1, ChronoUnit.HOURS));
    crearCita(tokenA, patientA.getId(), professionalA2.getId(),
        base.plus(2, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS));

    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(4, ChronoUnit.HOURS).toString())
            .param("professionalId", professionalA2.getId().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].professionalId").value(professionalA2.getId().toString()));
  }

  @Test
  void aislamientoCrossTenant() throws Exception {
    Instant base = base();
    crearCita(tokenA, patientA.getId(), professionalA1.getId(), base, base.plus(1, ChronoUnit.HOURS));
    crearCita(tokenB, patientB.getId(), professionalB.getId(),
        base.plus(1, ChronoUnit.HOURS), base.plus(2, ChronoUnit.HOURS));

    // La cita del tenant B cae dentro del rango pero es invisible para A.
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].patientId").value(patientA.getId().toString()));

    // Filtrar por un profesional de otro tenant → 404, no lista vacía.
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString())
            .param("professionalId", professionalB.getId().toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void rangoInvalidoDevuelve400() throws Exception {
    Instant base = base();

    // from posterior a to → 400 con mensaje claro.
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.plus(3, ChronoUnit.HOURS).toString())
            .param("to", base.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));

    // Parámetro requerido ausente → 400, no 500.
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.toString()))
        .andExpect(status().isBadRequest());

    // Fecha malformada → 400, no 500.
    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", "no-es-una-fecha")
            .param("to", base.toString()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rolNoAutorizadoDevuelve403() throws Exception {
    Instant base = base();

    mockMvc.perform(get("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    Instant base = base();

    mockMvc.perform(get("/api/v1/appointments")
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString()))
        .andExpect(status().isUnauthorized());
  }
}
