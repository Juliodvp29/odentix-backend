package com.julio.odentix.odentix_backend.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateAppointmentStatusRequest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
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

/**
 * Pruebas de la automatización de recuperación de espacio (FASE8-06):
 * al cancelarse una cita de alto valor con candidatos en lista de espera,
 * el sistema crea automáticamente una tarea para recepción.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Cancelar cita de alto valor con candidatos → tarea automática.</li>
 *   <li>Riesgo medio, sin candidatos o transición distinta → sin tarea.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SlotRecoveryAutomationIntegrationTest extends AbstractIntegrationTest {

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
  private AppointmentRepository appointmentRepository;

  @Autowired
  private WaitlistEntryRepository waitlistEntryRepository;

  @Autowired
  private TaskRepository taskRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private String tokenA;
  private Patient patientCancela;
  private Patient patientCandidata;
  private Professional professionalA;
  private UUID procedureId;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Recupera Auto Alfa", "9D0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@recupera-auto.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    procedureId = UUID.randomUUID();

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientCancela = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Cancela"));
      patientCandidata = patientRepository.save(new Patient(tenantA.getId(), "Luz", "Espera"));
      patientCandidata.setPhone("573009998877");
      patientCandidata = patientRepository.save(patientCandidata);
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Auto"));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Appointment crearCita(Patient patient, Instant start, Instant end, RiskLevel risk) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Appointment appointment = new Appointment(tenantA.getId(), patient, professionalA, start, end);
      appointment.setProcedureId(procedureId);
      appointment.setEstimatedValueCop(new BigDecimal("850000.00"));
      appointment.setRiskLevel(risk);
      appointment.setStatus(AppointmentStatus.programada);
      return appointmentRepository.saveAndFlush(appointment);
    } finally {
      TenantContext.clear();
    }
  }

  private void crearCandidata(Patient patient, Instant from, Instant to) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      WaitlistEntry entry = new WaitlistEntry(tenantA.getId(), patient);
      entry.setProcedureId(procedureId);
      entry.setDesiredFrom(from);
      entry.setDesiredTo(to);
      entry.setStatus(WaitlistStatus.activa);
      waitlistEntryRepository.saveAndFlush(entry);
    } finally {
      TenantContext.clear();
    }
  }

  private void cambiarEstado(String citaId, AppointmentStatus estado) throws Exception {
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateAppointmentStatusRequest(estado))))
        .andExpect(status().isOk());
  }

  private java.util.List<com.julio.odentix.odentix_backend.task.entity.Task> tareas() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return taskRepository.findAll();
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE8-06)
  // ---------------------------------------------------------------------------

  @Test
  void cancelaAltoValorConCandidatoCreaTareaAutomatica() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, RiskLevel.alto);
    crearCandidata(patientCandidata, start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));

    cambiarEstado(cita.getId().toString(), AppointmentStatus.cancelada);

    var tareas = tareas();
    assertThat(tareas).hasSize(1);
    var tarea = tareas.get(0);
    assertThat(tarea.getTitle()).contains("Recuperar");
    assertThat(tarea.getDescription()).contains("Luz Espera");
    assertThat(tarea.getDescription()).contains("573009998877");
    assertThat(tarea.getRelatedEntityType()).isEqualTo("appointment");
    assertThat(tarea.getRelatedEntityId()).isEqualTo(cita.getId());
    assertThat(tarea.getPriority()).isEqualTo(TaskPriority.alta);
    assertThat(tarea.getStatus()).isEqualTo(TaskStatus.pendiente);
    assertThat(tarea.getDueAt()).isEqualTo(start);
  }

  @Test
  void riesgoMedioConCandidatoNoCreaTarea() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, RiskLevel.medio);
    crearCandidata(patientCandidata, start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));

    cambiarEstado(cita.getId().toString(), AppointmentStatus.cancelada);

    assertThat(tareas()).isEmpty();
  }

  @Test
  void altoValorSinCandidatosNoCreaTarea() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, RiskLevel.alto);

    cambiarEstado(cita.getId().toString(), AppointmentStatus.cancelada);

    assertThat(tareas()).isEmpty();
  }

  @Test
  void confirmarNoCreaTareaDeRecuperacion() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, RiskLevel.alto);
    crearCandidata(patientCandidata, start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));

    cambiarEstado(cita.getId().toString(), AppointmentStatus.confirmada);

    assertThat(tareas()).isEmpty();
  }
}
