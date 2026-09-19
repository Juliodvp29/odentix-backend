package com.julio.odentix.odentix_backend.opportunity;

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
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de la regla `espacio_disponible` (FASE9-02): al cancelarse una cita
 * con candidatos en lista de espera, el evento genera la oportunidad.
 *
 * <p>Las aserciones se acotan a la cita propia (la BD se comparte entre suites
 * y otros tests también cancelan citas).
 */
@AutoConfigureMockMvc
class SlotOpportunityListenerIntegrationTest extends AbstractIntegrationTest {

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
  private OpportunityRepository opportunityRepository;

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

    tenantA = tenantRepository.save(new Tenant("Clínica Espacio Alfa", "9F0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@espacio-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    procedureId = UUID.randomUUID();

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientCancela = patientRepository.save(new Patient(tenantA.getId(), "Ceco", "Cancela"));
      patientCandidata = patientRepository.save(new Patient(tenantA.getId(), "Pronta", "Espera"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Hueco"));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Appointment crearCita(Patient patient, Instant start, Instant end, BigDecimal valor) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Appointment cita = new Appointment(tenantA.getId(), patient, professionalA, start, end);
      cita.setProcedureId(procedureId);
      cita.setEstimatedValueCop(valor);
      cita.setRiskLevel(RiskLevel.medio);
      cita.setStatus(AppointmentStatus.programada);
      return appointmentRepository.saveAndFlush(cita);
    } finally {
      TenantContext.clear();
    }
  }

  private void crearCandidata(Instant from, Instant to) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      WaitlistEntry entry = new WaitlistEntry(tenantA.getId(), patientCandidata);
      entry.setProcedureId(procedureId);
      entry.setDesiredFrom(from);
      entry.setDesiredTo(to);
      entry.setStatus(WaitlistStatus.activa);
      waitlistEntryRepository.saveAndFlush(entry);
    } finally {
      TenantContext.clear();
    }
  }

  private void cancelarCita(String citaId) throws Exception {
    mockMvc.perform(patch("/api/v1/appointments/" + citaId + "/status")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateAppointmentStatusRequest(AppointmentStatus.cancelada))))
        .andExpect(status().isOk());
  }

  private List<Opportunity> oppsPara(UUID citaId) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return opportunityRepository.findAll().stream()
          .filter(o -> citaId.equals(o.getRelatedEntityId()))
          .toList();
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE9-02, regla espacio_disponible)
  // ---------------------------------------------------------------------------

  @Test
  void cancelacionConCandidatosGeneraOportunidad() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, new BigDecimal("600000.00"));
    crearCandidata(start.minus(1, ChronoUnit.HOURS), end.plus(1, ChronoUnit.HOURS));

    cancelarCita(cita.getId().toString());

    List<Opportunity> opps = oppsPara(cita.getId());
    assertThat(opps).hasSize(1);
    assertThat(opps.get(0).getType()).isEqualTo(OpportunityType.espacio_disponible);
    assertThat(opps.get(0).getPriority()).isEqualTo((short) 4);
    assertThat(opps.get(0).getEstimatedValueCop())
        .isEqualByComparingTo(new BigDecimal("600000.00"));
    assertThat(opps.get(0).getRelatedEntityType()).isEqualTo("appointment");
    assertThat(opps.get(0).getTenantId()).isEqualTo(tenantA.getId());

    // Segunda cancelación (no-op, ya cancelada): el evento solo se publica en
    // transición real, así que no duplica.
    cancelarCita(cita.getId().toString());
    assertThat(oppsPara(cita.getId())).hasSize(1);
  }

  @Test
  void cancelacionSinCandidatosNoGeneraOportunidad() throws Exception {
    Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(2, ChronoUnit.DAYS);
    Instant end = start.plus(60, ChronoUnit.MINUTES);
    Appointment cita = crearCita(patientCancela, start, end, new BigDecimal("600000.00"));

    cancelarCita(cita.getId().toString());

    assertThat(oppsPara(cita.getId())).isEmpty();
  }
}
