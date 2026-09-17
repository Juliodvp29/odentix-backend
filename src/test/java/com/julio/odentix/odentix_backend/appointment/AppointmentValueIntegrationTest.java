package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Pruebas de integración para el valor estimado de la agenda (FASE3-05)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: el endpoint de agregación devuelve un total
 * coherente con la suma manual de las citas del rango.
 */
@AutoConfigureMockMvc
class AppointmentValueIntegrationTest extends AbstractIntegrationTest {

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

    tenantA = tenantRepository.save(new Tenant("Clínica Valor Alfa", "907111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@valor-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@valor-alfa.com", "ClaveSegura789!", "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Valor Beta", "908333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@valor-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
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

  private String crearCita(String token, UUID patientId, UUID professionalId,
      Instant startsAt, Instant endsAt, String valorCop) throws Exception {
    CreateAppointmentRequest request =
        new CreateAppointmentRequest(patientId, professionalId, startsAt, endsAt);
    request.setEstimatedValueCop(new BigDecimal(valorCop));
    MvcResult result = mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return body.get("id").toString();
  }

  private Map<?, ?> resumir(String token, Instant from, Instant to) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/appointments/estimated-value")
            .header("Authorization", "Bearer " + token)
            .param("from", from.toString())
            .param("to", to.toString()))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
  }

  @Test
  void totalCoincideConSumaManualYExcluyeCanceladas() throws Exception {
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);

    crearCita(tokenA, patientA.getId(), professionalA.getId(),
        base, base.plus(1, ChronoUnit.HOURS), "150000.00");
    crearCita(tokenA, patientA.getId(), professionalA.getId(),
        base.plus(2, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS), "200000.00");
    // Fuera del rango: no suma.
    crearCita(tokenA, patientA.getId(), professionalA.getId(),
        base.plus(26, ChronoUnit.HOURS), base.plus(27, ChronoUnit.HOURS), "50000.00");
    // Cancelada dentro del rango: espacio liberado, no suma.
    String canceladaId = crearCita(tokenA, patientA.getId(), professionalA.getId(),
        base.plus(4, ChronoUnit.HOURS), base.plus(5, ChronoUnit.HOURS), "999999.00");
    mockMvc.perform(patch("/api/v1/appointments/" + canceladaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.cancelada))))
        .andExpect(status().isOk());

    Map<?, ?> resumen = resumir(tokenA, base.minus(1, ChronoUnit.HOURS), base.plus(6, ChronoUnit.HOURS));

    assertThat(new BigDecimal(resumen.get("totalCop").toString()))
        .isEqualByComparingTo(new BigDecimal("350000.00"));
    assertThat(((Number) resumen.get("appointmentCount")).longValue()).isEqualTo(2L);

    // Rango vacío: total 0 y conteo 0, sin nulls.
    Map<?, ?> vacio = resumir(tokenA, base.plus(30, ChronoUnit.HOURS), base.plus(31, ChronoUnit.HOURS));
    assertThat(new BigDecimal(vacio.get("totalCop").toString()))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(((Number) vacio.get("appointmentCount")).longValue()).isZero();
  }

  @Test
  void crossTenantNoContaminaElTotal() throws Exception {
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);

    crearCita(tokenA, patientA.getId(), professionalA.getId(),
        base, base.plus(1, ChronoUnit.HOURS), "150000.00");
    crearCita(tokenB, patientB.getId(), professionalB.getId(),
        base.plus(1, ChronoUnit.HOURS), base.plus(2, ChronoUnit.HOURS), "1000000.00");

    Map<?, ?> resumen = resumir(tokenA, base.minus(1, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS));

    assertThat(new BigDecimal(resumen.get("totalCop").toString()))
        .isEqualByComparingTo(new BigDecimal("150000.00"));
    assertThat(((Number) resumen.get("appointmentCount")).longValue()).isEqualTo(1L);
  }

  @Test
  void rangoInvalidoDevuelve400() throws Exception {
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);

    mockMvc.perform(get("/api/v1/appointments/estimated-value")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", base.plus(3, ChronoUnit.HOURS).toString())
            .param("to", base.toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void rolNoAutorizadoDevuelve403() throws Exception {
    Instant base = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);

    mockMvc.perform(get("/api/v1/appointments/estimated-value")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .param("from", base.minus(1, ChronoUnit.HOURS).toString())
            .param("to", base.plus(3, ChronoUnit.HOURS).toString()))
        .andExpect(status().isForbidden());
  }
}
