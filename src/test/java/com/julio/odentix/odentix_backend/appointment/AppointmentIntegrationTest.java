package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
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
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para citas y prevención de solapamiento (FASE3-02)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Crear cita válida exitosamente (201 Created).</li>
 *   <li>Crear dos citas solapadas para el mismo profesional devuelve 409 Conflict.</li>
 *   <li>Citas contiguas para el mismo profesional están permitidas.</li>
 *   <li>Cita solapada con una cita cancelada/no_show está permitida (espacio liberado).</li>
 *   <li>Citas paralelas para profesionales distintos están permitidas.</li>
 *   <li>Validación de rango temporal (ends_at > starts_at).</li>
 *   <li>Aislamiento cross-tenant (no se pueden usar recursos de otro tenant).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class AppointmentIntegrationTest extends AbstractIntegrationTest {

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
  private RoomRepository roomRepository;

  @Autowired
  private AppointmentRepository appointmentRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private Patient patientA;
  private Patient patientB;
  private Professional professionalA1;
  private Professional professionalA2;
  private Room roomA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Citas Alfa", "901999888-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Citas Beta", "902777666-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Carlos", "Sánchez"));
      professionalA1 = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Mario Bros"));
      professionalA2 = professionalRepository.save(new Professional(tenantA.getId(), "Dra. Peach"));
      roomA = roomRepository.save(new Room(tenantA.getId(), "Sillón 1"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Luigi", "Bros"));
    } finally {
      TenantContext.clear();
    }
  }

  private CreateAppointmentRequest solicitudCita(
      UUID patientId, UUID professionalId, UUID roomId, Instant startsAt, Instant endsAt) {
    CreateAppointmentRequest request = new CreateAppointmentRequest(patientId, professionalId, startsAt, endsAt);
    request.setRoomId(roomId);
    request.setEstimatedValueCop(new BigDecimal("150000.00"));
    request.setRiskLevel(RiskLevel.medio);
    request.setNotes("Primera consulta de valoración");
    return request;
  }

  @Test
  void crearCitaValidaExitoso() throws Exception {
    Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    Instant fin = ahora.plus(45, ChronoUnit.MINUTES);

    CreateAppointmentRequest request = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), ahora, fin);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.patientName").value("Carlos Sánchez"))
        .andExpect(jsonPath("$.professionalId").value(professionalA1.getId().toString()))
        .andExpect(jsonPath("$.professionalName").value("Dr. Mario Bros"))
        .andExpect(jsonPath("$.roomId").value(roomA.getId().toString()))
        .andExpect(jsonPath("$.roomName").value("Sillón 1"))
        .andExpect(jsonPath("$.status").value("programada"))
        .andExpect(jsonPath("$.riskLevel").value("medio"))
        .andExpect(jsonPath("$.estimatedValueCop").value(150000.00));
  }

  @Test
  void solapamientoMismoProfesionalDevuelve409Conflict() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant fin = inicio.plus(60, ChronoUnit.MINUTES);

    // 1. Crear primera cita (10:00 - 11:00)
    CreateAppointmentRequest cita1 = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicio, fin);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cita1)))
        .andExpect(status().isCreated());

    // 2. Intentar crear segunda cita solapada para el mismo profesional (10:30 - 11:30)
    Instant inicioSolapado = inicio.plus(30, ChronoUnit.MINUTES);
    Instant finSolapado = fin.plus(30, ChronoUnit.MINUTES);

    CreateAppointmentRequest cita2 = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicioSolapado, finSolapado);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cita2)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("solapa")));
  }

  @Test
  void citasContiguasMismoProfesionalPermitidas() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(3, ChronoUnit.DAYS);
    Instant fin = inicio.plus(30, ChronoUnit.MINUTES);

    // Cita 1: 10:00 - 10:30
    CreateAppointmentRequest cita1 = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicio, fin);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cita1)))
        .andExpect(status().isCreated());

    // Cita 2: 10:30 - 11:00 (inicia exactamente cuando termina la primera)
    CreateAppointmentRequest cita2 = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), fin, fin.plus(30, ChronoUnit.MINUTES));

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cita2)))
        .andExpect(status().isCreated());
  }

  @Test
  void citaSolapadaConCitaCanceladaPermitida() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(4, ChronoUnit.DAYS);
    Instant fin = inicio.plus(45, ChronoUnit.MINUTES);

    // 1. Crear y cancelar cita previa
    TenantContext.setTenantId(tenantA.getId());
    try {
      Appointment citaCancelada = new Appointment(tenantA.getId(), patientA, professionalA1, inicio, fin);
      citaCancelada.setStatus(AppointmentStatus.cancelada);
      appointmentRepository.saveAndFlush(citaCancelada);
    } finally {
      TenantContext.clear();
    }

    // 2. Agendar en el mismo horario liberado
    CreateAppointmentRequest nuevaCita = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicio, fin);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(nuevaCita)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("programada"));
  }

  @Test
  void citasMismoHorarioDiferentesProfesionalesPermitidas() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.DAYS);
    Instant fin = inicio.plus(60, ChronoUnit.MINUTES);

    // Cita con Profesional 1
    CreateAppointmentRequest citaProf1 = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicio, fin);
    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(citaProf1)))
        .andExpect(status().isCreated());

    // Cita simultánea con Profesional 2
    CreateAppointmentRequest citaProf2 = solicitudCita(
        patientA.getId(), professionalA2.getId(), null, inicio, fin);
    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(citaProf2)))
        .andExpect(status().isCreated());
  }

  @Test
  void rangoTemporalInvalidoDevuelve400BadRequest() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    Instant finAnterior = inicio.minus(30, ChronoUnit.MINUTES);

    CreateAppointmentRequest request = solicitudCita(
        patientA.getId(), professionalA1.getId(), roomA.getId(), inicio, finAnterior);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void aislamientoCrossTenantNoPermiteAgendarConPacienteDeOtroTenant() throws Exception {
    Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    Instant fin = inicio.plus(30, ChronoUnit.MINUTES);

    // Tenant A intenta usar patientB
    CreateAppointmentRequest request = solicitudCita(
        patientB.getId(), professionalA1.getId(), roomA.getId(), inicio, fin);

    mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));
  }
}
