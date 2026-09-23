package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
 * Pruebas de integración para la recuperación de espacio al cancelar una cita
 * (FASE3-07) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Cancelar una cita marcada como de alto valor devuelve los candidatos compatibles de la lista de espera.</li>
 *   <li>Endpoint {@code GET /api/v1/appointments/{id}/waitlist-candidates} bajo demanda.</li>
 *   <li>Filtrado correcto por procedimiento, rango temporal y exclusión del paciente cancelador.</li>
 *   <li>Exclusión de entradas no activas.</li>
 *   <li>Aislamiento cross-tenant estricto.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SlotRecoveryIntegrationTest extends AbstractIntegrationTest {

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

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private Patient patientCancela;
  private Patient patientInteresado1;
  private Patient patientInteresado2;
  private Patient patientOtroTenant;
  private Professional professionalA;
  private UUID procedureImplante;
  private UUID procedureOrtodoncia;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Recupera Alfa", "901555666-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@recupera-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Recupera Beta", "902777888-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@recupera-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    procedureImplante = UUID.randomUUID();
    procedureOrtodoncia = UUID.randomUUID();

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientCancela = patientRepository.save(new Patient(tenantA.getId(), "Homero", "Simpson"));
      Patient interesado1 = new Patient(tenantA.getId(), "Ned", "Flanders");
      interesado1.setPhone("3001234567");
      patientInteresado1 = patientRepository.save(interesado1);
      patientInteresado2 = patientRepository.save(new Patient(tenantA.getId(), "Moe", "Szyslak"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Julius Hibbert"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientOtroTenant = patientRepository.save(new Patient(tenantB.getId(), "Barnaby", "Jones"));
    } finally {
      TenantContext.clear();
    }
  }

  private Appointment crearCita(
      UUID tenantId, Patient patient, Professional professional, UUID procedureId,
      Instant start, Instant end, BigDecimal valor, RiskLevel risk) {
    TenantContext.setTenantId(tenantId);
    try {
      Appointment appointment = new Appointment(tenantId, patient, professional, start, end);
      appointment.setProcedureId(procedureId);
      appointment.setEstimatedValueCop(valor);
      appointment.setRiskLevel(risk);
      appointment.setStatus(AppointmentStatus.programada);
      return appointmentRepository.saveAndFlush(appointment);
    } finally {
      TenantContext.clear();
    }
  }

  private WaitlistEntry crearEntradaEspera(
      UUID tenantId, Patient patient, UUID procedureId, Instant from, Instant to, WaitlistStatus status) {
    TenantContext.setTenantId(tenantId);
    try {
      WaitlistEntry entry = new WaitlistEntry(tenantId, patient);
      entry.setProcedureId(procedureId);
      entry.setDesiredFrom(from);
      entry.setDesiredTo(to);
      entry.setStatus(status);
      return waitlistEntryRepository.saveAndFlush(entry);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void cancelarCitaAltoValorDevuelveCandidatosCompatiblesDeListaDeEspera() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    // Cita de alto valor para Homero
    Appointment citaAltoValor = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("850000.00"), RiskLevel.alto);

    // Candidato compatible: Ned Flanders interesado en implante en esa ventana
    WaitlistEntry entryCompatible = crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureImplante,
        slotStart.minus(2, ChronoUnit.HOURS), slotEnd.plus(2, ChronoUnit.HOURS), WaitlistStatus.activa);

    UpdateAppointmentStatusRequest request = new UpdateAppointmentStatusRequest();
    request.setStatus(AppointmentStatus.cancelada);

    mockMvc.perform(patch("/api/v1/appointments/" + citaAltoValor.getId() + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cancelada"))
        .andExpect(jsonPath("$.waitlistCandidates").isArray())
        .andExpect(jsonPath("$.waitlistCandidates.length()").value(1))
        .andExpect(jsonPath("$.waitlistCandidates[0].id").value(entryCompatible.getId().toString()))
        .andExpect(jsonPath("$.waitlistCandidates[0].patientId").value(patientInteresado1.getId().toString()))
        .andExpect(jsonPath("$.waitlistCandidates[0].patientName").value("Ned Flanders"))
        .andExpect(jsonPath("$.waitlistCandidates[0].patientPhone").value("3001234567"));
  }

  @Test
  void endpointWaitlistCandidatesDevuelveCandidatosEnOrdenFifo() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(3, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(45, ChronoUnit.MINUTES);

    Appointment cita = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureOrtodoncia,
        slotStart, slotEnd, new BigDecimal("300000.00"), RiskLevel.medio);

    // Candidato 1: registrado primero
    WaitlistEntry entry1 = crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureOrtodoncia,
        slotStart.minus(1, ChronoUnit.HOURS), slotEnd.plus(1, ChronoUnit.HOURS), WaitlistStatus.activa);

    // Candidato 2: registrado después
    WaitlistEntry entry2 = crearEntradaEspera(
        tenantA.getId(), patientInteresado2, procedureOrtodoncia,
        slotStart.minus(1, ChronoUnit.HOURS), slotEnd.plus(1, ChronoUnit.HOURS), WaitlistStatus.activa);

    mockMvc.perform(get("/api/v1/appointments/" + cita.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(entry1.getId().toString()))
        .andExpect(jsonPath("$[1].id").value(entry2.getId().toString()));
  }

  @Test
  void filtroProcedimientoDescartaCandidatosConOtroProcedimiento() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(4, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    Appointment citaImplante = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("600000.00"), RiskLevel.medio);

    // Interesado en otro procedimiento (ortodoncia) -> descartado
    crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureOrtodoncia,
        slotStart, slotEnd, WaitlistStatus.activa);

    // Interesado sin procedimiento específico (null = cualquiera) -> compatible
    WaitlistEntry entryCualquiera = crearEntradaEspera(
        tenantA.getId(), patientInteresado2, null,
        slotStart, slotEnd, WaitlistStatus.activa);

    mockMvc.perform(get("/api/v1/appointments/" + citaImplante.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(entryCualquiera.getId().toString()));
  }

  @Test
  void filtroHorarioDescartaCandidatosSinDisponibilidadSolapada() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    Appointment cita = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("500000.00"), RiskLevel.medio);

    // Disponibilidad que no se solapa (antes de que empiece la cita)
    crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureImplante,
        slotStart.minus(4, ChronoUnit.HOURS), slotStart.minus(2, ChronoUnit.HOURS), WaitlistStatus.activa);

    // Disponibilidad que sí se solapa
    WaitlistEntry entrySolapada = crearEntradaEspera(
        tenantA.getId(), patientInteresado2, procedureImplante,
        slotStart.plus(15, ChronoUnit.MINUTES), slotEnd.plus(2, ChronoUnit.HOURS), WaitlistStatus.activa);

    mockMvc.perform(get("/api/v1/appointments/" + cita.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(entrySolapada.getId().toString()));
  }

  @Test
  void excluyeAlPacienteQueCanceloLaCita() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(6, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    Appointment citaHomero = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("700000.00"), RiskLevel.alto);

    // Homero también tenía una entrada en lista de espera para el mismo procedimiento
    crearEntradaEspera(
        tenantA.getId(), patientCancela, procedureImplante,
        slotStart, slotEnd, WaitlistStatus.activa);

    // Ned Flanders también está en lista de espera
    WaitlistEntry entryNed = crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureImplante,
        slotStart, slotEnd, WaitlistStatus.activa);

    mockMvc.perform(get("/api/v1/appointments/" + citaHomero.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(entryNed.getId().toString()));
  }

  @Test
  void excluyeEntradasNoActivas() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(7, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    Appointment cita = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("400000.00"), RiskLevel.medio);

    // Entradas no activas
    crearEntradaEspera(
        tenantA.getId(), patientInteresado1, procedureImplante,
        slotStart, slotEnd, WaitlistStatus.convertida);
    crearEntradaEspera(
        tenantA.getId(), patientInteresado2, procedureImplante,
        slotStart, slotEnd, WaitlistStatus.descartada);

    mockMvc.perform(get("/api/v1/appointments/" + cita.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void aislamientoCrossTenantImpideAccesoACitasYCandidatosDeOtroTenant() throws Exception {
    Instant slotStart = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(8, ChronoUnit.DAYS);
    Instant slotEnd = slotStart.plus(60, ChronoUnit.MINUTES);

    Appointment citaA = crearCita(
        tenantA.getId(), patientCancela, professionalA, procedureImplante,
        slotStart, slotEnd, new BigDecimal("600000.00"), RiskLevel.alto);

    // Entrada compatible pero en Tenant B
    crearEntradaEspera(
        tenantB.getId(), patientOtroTenant, procedureImplante,
        slotStart, slotEnd, WaitlistStatus.activa);

    // Tenant A consulta su cita: no debe ver la entrada de Tenant B
    mockMvc.perform(get("/api/v1/appointments/" + citaA.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Tenant B intenta consultar la cita de Tenant A: 404
    mockMvc.perform(get("/api/v1/appointments/" + citaA.getId() + "/waitlist-candidates")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
  }
}
