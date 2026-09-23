package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.ConvertWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class WaitlistConversionIntegrationTest extends AbstractIntegrationTest {

  private static final Instant SOURCE_START = Instant.parse("2031-05-10T14:00:00Z");
  private static final Instant SOURCE_END = SOURCE_START.plus(1, ChronoUnit.HOURS);

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

  @Autowired
  private WaitlistEntryRepository waitlistEntryRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private String tokenEspecialistaA;
  private Patient waitlistPatientA;
  private Patient sourcePatientA;
  private Professional professionalA;
  private Room roomA;
  private UUID procedureId;
  private Appointment sourceA;
  private WaitlistEntry entryA;
  private Appointment sourceB;
  private WaitlistEntry entryB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    procedureId = UUID.randomUUID();

    tenantA = tenantRepository.save(new Tenant("Clínica Conversión Alfa", "907771222-1"));
    User receptionA = userService.createUser(
        tenantA.getId(),
        "recepcion@conversion-alfa.com",
        "ClaveSegura123!",
        "Recepción Conversión Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(receptionA);
    User externalA = userService.createUser(
        tenantA.getId(),
        "externo@conversion-alfa.com",
        "ClaveSegura789!",
        "Externo Conversión Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(externalA);

    tenantB = tenantRepository.save(new Tenant("Clínica Conversión Beta", "908553444-2"));
    User receptionB = userService.createUser(
        tenantB.getId(),
        "recepcion@conversion-beta.com",
        "ClaveSegura456!",
        "Recepción Conversión Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(receptionB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      waitlistPatientA = patientRepository.save(new Patient(tenantA.getId(), "Paciente", "Convertido"));
      sourcePatientA = patientRepository.save(new Patient(tenantA.getId(), "Paciente", "Origen"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dra. Conversión"));
      roomA = roomRepository.save(new Room(tenantA.getId(), "Consultorio de conversión"));
      sourceA = crearCita(
          tenantA.getId(),
          sourcePatientA,
          professionalA,
          roomA,
          procedureId,
          SOURCE_START,
          SOURCE_END,
          AppointmentStatus.cancelada);
      entryA = crearEntrada(
          tenantA.getId(),
          waitlistPatientA,
          WaitlistStatus.activa,
          procedureId,
          SOURCE_START,
          SOURCE_END);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient waitlistPatientB = patientRepository.save(new Patient(tenantB.getId(), "Paciente", "Beta"));
      Patient sourcePatientB = patientRepository.save(new Patient(tenantB.getId(), "Paciente", "Origen Beta"));
      Professional professionalB = professionalRepository.save(new Professional(tenantB.getId(), "Dr. Beta"));
      Room roomB = roomRepository.save(new Room(tenantB.getId(), "Consultorio Beta"));
      sourceB = crearCita(
          tenantB.getId(),
          sourcePatientB,
          professionalB,
          roomB,
          procedureId,
          SOURCE_START,
          SOURCE_END,
          AppointmentStatus.cancelada);
      entryB = crearEntrada(
          tenantB.getId(),
          waitlistPatientB,
          WaitlistStatus.activa,
          procedureId,
          SOURCE_START,
          SOURCE_END);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void convertirCreaCitaConDatosDerivadosYActualizaEntrada() throws Exception {
    ConvertWaitlistEntryRequest request = new ConvertWaitlistEntryRequest(sourceA.getId());
    request.setNotes("Cita recuperada desde lista de espera");

    MvcResult result = mockMvc.perform(post("/api/v1/waitlist/{id}/convert", entryA.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.patientId").value(waitlistPatientA.getId().toString()))
        .andExpect(jsonPath("$.professionalId").value(professionalA.getId().toString()))
        .andExpect(jsonPath("$.roomId").value(roomA.getId().toString()))
        .andExpect(jsonPath("$.procedureId").value(procedureId.toString()))
        .andExpect(jsonPath("$.startsAt").value(SOURCE_START.toString()))
        .andExpect(jsonPath("$.endsAt").value(SOURCE_END.toString()))
        .andExpect(jsonPath("$.status").value("programada"))
        .andExpect(jsonPath("$.notes").value("Cita recuperada desde lista de espera"))
        .andReturn();

    JsonNode responseNode = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID createdAppointmentId = UUID.fromString(responseNode.get("id").asText());
    assertThat(responseNode.get("patientId").asText()).isEqualTo(waitlistPatientA.getId().toString());
    assertThat(responseNode.get("patientId").asText()).isNotEqualTo(sourcePatientA.getId().toString());
    assertThat(createdAppointmentId).isNotEqualTo(sourceA.getId());

    mockMvc.perform(get("/api/v1/waitlist/{id}", entryA.getId())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("convertida"))
        .andExpect(jsonPath("$.convertedAt").isNotEmpty())
        .andExpect(jsonPath("$.convertedAppointmentId").value(createdAppointmentId.toString()));

    WaitlistEntry persisted = obtenerEntradaA(entryA.getId());
    assertThat(persisted.getConvertedAppointment().getId()).isEqualTo(createdAppointmentId);
    assertThat(persisted.getRecoveredFromAppointment().getId()).isEqualTo(sourceA.getId());
    assertThat(notificationRepository.findAll().stream()
        .filter(notification -> waitlistPatientA.getId().equals(notification.getPatientId()))
        .filter(notification -> "cita_agendada".equals(notification.getTemplateKey()))
        .count()).isEqualTo(1);
  }

  @Test
  void convertirPermiteEntradaContactada() throws Exception {
    WaitlistEntry contacted = crearEntrada(
        tenantA.getId(),
        waitlistPatientA,
        WaitlistStatus.contactado,
        procedureId,
        SOURCE_START,
        SOURCE_END);

    convertir(contacted, sourceA.getId(), tokenA)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.patientId").value(waitlistPatientA.getId().toString()));

    assertThat(obtenerEntradaA(contacted.getId()).getStatus()).isEqualTo(WaitlistStatus.convertida);
  }

  @Test
  void convertirRechazaProcedimientoIncompatible() throws Exception {
    WaitlistEntry incompatible = crearEntrada(
        tenantA.getId(),
        waitlistPatientA,
        WaitlistStatus.activa,
        UUID.randomUUID(),
        SOURCE_START,
        SOURCE_END);

    convertir(incompatible, sourceA.getId(), tokenA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("procedimiento")));

    WaitlistEntry persisted = obtenerEntradaA(incompatible.getId());
    assertThat(persisted.getStatus()).isEqualTo(WaitlistStatus.activa);
    assertThat(persisted.getConvertedAppointment()).isNull();
    assertThat(contarCitasA()).isEqualTo(1);
  }

  @Test
  void convertirRechazaVentanaIncompatible() throws Exception {
    WaitlistEntry incompatible = crearEntrada(
        tenantA.getId(),
        waitlistPatientA,
        WaitlistStatus.activa,
        procedureId,
        SOURCE_END.plus(1, ChronoUnit.HOURS),
        SOURCE_END.plus(1, ChronoUnit.DAYS));

    convertir(incompatible, sourceA.getId(), tokenA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("rango")));

    assertThat(obtenerEntradaA(incompatible.getId()).getStatus()).isEqualTo(WaitlistStatus.activa);
    assertThat(contarCitasA()).isEqualTo(1);
  }

  @Test
  void convertirRechazaCitaOrigenNoCancelada() throws Exception {
    Appointment scheduled = crearCita(
        tenantA.getId(),
        sourcePatientA,
        professionalA,
        roomA,
        procedureId,
        SOURCE_START.plus(2, ChronoUnit.DAYS),
        SOURCE_END.plus(2, ChronoUnit.DAYS),
        AppointmentStatus.programada);
    WaitlistEntry compatible = crearEntrada(
        tenantA.getId(),
        waitlistPatientA,
        WaitlistStatus.contactado,
        procedureId,
        scheduled.getStartsAt(),
        scheduled.getEndsAt());

    convertir(compatible, scheduled.getId(), tokenA)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("cancelada")));

    assertThat(obtenerEntradaA(compatible.getId()).getStatus()).isEqualTo(WaitlistStatus.contactado);
  }

  @Test
  void aislamientoCrossTenantOcultaEntradaYCitaOrigen() throws Exception {
    convertir(entryA, sourceA.getId(), tokenB)
        .andExpect(status().isNotFound());
    convertir(entryB, sourceB.getId(), tokenA)
        .andExpect(status().isNotFound());
    convertir(entryA, sourceB.getId(), tokenA)
        .andExpect(status().isNotFound());
  }

  @Test
  void convertirRechazaEntradaDescartada() throws Exception {
    WaitlistEntry discarded = crearEntrada(
        tenantA.getId(),
        waitlistPatientA,
        WaitlistStatus.descartada,
        procedureId,
        SOURCE_START,
        SOURCE_END);

    convertir(discarded, sourceA.getId(), tokenA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("descartada")));

    assertThat(contarCitasA()).isEqualTo(1);
  }

  @Test
  void repetirConversionDevuelveMismaCitaSinDuplicar() throws Exception {
    MvcResult first = convertir(entryA, sourceA.getId(), tokenA)
        .andExpect(status().isCreated())
        .andReturn();
    MvcResult second = convertir(entryA, sourceA.getId(), tokenA)
        .andExpect(status().isOk())
        .andReturn();

    assertThat(readId(second)).isEqualTo(readId(first));
    assertThat(contarCitasA()).isEqualTo(2);
  }

  @Test
  void slotOccupiedDevuelve409YRevierteLaEntrada() throws Exception {
    crearCita(
        tenantA.getId(),
        sourcePatientA,
        professionalA,
        roomA,
        procedureId,
        SOURCE_START.plus(15, ChronoUnit.MINUTES),
        SOURCE_END.plus(15, ChronoUnit.MINUTES),
        AppointmentStatus.programada);

    convertir(entryA, sourceA.getId(), tokenA)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("solapa")));

    WaitlistEntry persisted = obtenerEntradaA(entryA.getId());
    assertThat(persisted.getStatus()).isEqualTo(WaitlistStatus.activa);
    assertThat(persisted.getConvertedAppointment()).isNull();
    assertThat(persisted.getConvertedAt()).isNull();
    assertThat(contarCitasA()).isEqualTo(2);
    assertThat(notificationRepository.findAll().stream()
        .filter(notification -> waitlistPatientA.getId().equals(notification.getPatientId()))
        .filter(notification -> "cita_agendada".equals(notification.getTemplateKey()))
        .count()).isZero();
  }

  @Test
  void conversionesConcurrentesSeSerializanYDevuelvenLaMismaCita() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Future<MvcResult> firstFuture = executor.submit(() -> {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return convertir(entryA, sourceA.getId(), tokenA).andReturn();
      });
      Future<MvcResult> secondFuture = executor.submit(() -> {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return convertir(entryA, sourceA.getId(), tokenA).andReturn();
      });

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      MvcResult first = firstFuture.get(15, TimeUnit.SECONDS);
      MvcResult second = secondFuture.get(15, TimeUnit.SECONDS);
      assertThat(List.of(first.getResponse().getStatus(), second.getResponse().getStatus()))
          .containsExactlyInAnyOrder(200, 201);
      assertThat(readId(second)).isEqualTo(readId(first));
      assertThat(contarCitasA()).isEqualTo(2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void conversionValidaAutenticacionYFuenteRequerida() throws Exception {
    ConvertWaitlistEntryRequest request = new ConvertWaitlistEntryRequest(sourceA.getId());

    mockMvc.perform(post("/api/v1/waitlist/{id}/convert", entryA.getId())
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/waitlist/{id}/convert", entryA.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());

    mockMvc.perform(post("/api/v1/waitlist/{id}/convert", entryA.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.sourceAppointmentId").exists());
  }

  private org.springframework.test.web.servlet.ResultActions convertir(
      WaitlistEntry entry, UUID sourceAppointmentId, String token) throws Exception {
    ConvertWaitlistEntryRequest request = new ConvertWaitlistEntryRequest(sourceAppointmentId);
    return mockMvc.perform(post("/api/v1/waitlist/{id}/convert", entry.getId())
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)));
  }

  private Appointment crearCita(
      UUID tenantId,
      Patient patient,
      Professional professional,
      Room room,
      UUID appointmentProcedureId,
      Instant startsAt,
      Instant endsAt,
      AppointmentStatus status) {
    TenantContext.setTenantId(tenantId);
    try {
      Appointment appointment = new Appointment(tenantId, patient, professional, startsAt, endsAt);
      appointment.setRoom(room);
      appointment.setProcedureId(appointmentProcedureId);
      appointment.setStatus(status);
      return appointmentRepository.saveAndFlush(appointment);
    } finally {
      TenantContext.clear();
    }
  }

  private WaitlistEntry crearEntrada(
      UUID tenantId,
      Patient patient,
      WaitlistStatus status,
      UUID entryProcedureId,
      Instant desiredFrom,
      Instant desiredTo) {
    TenantContext.setTenantId(tenantId);
    try {
      WaitlistEntry entry = new WaitlistEntry(tenantId, patient);
      entry.setStatus(status);
      entry.setProcedureId(entryProcedureId);
      entry.setDesiredFrom(desiredFrom);
      entry.setDesiredTo(desiredTo);
      return waitlistEntryRepository.saveAndFlush(entry);
    } finally {
      TenantContext.clear();
    }
  }

  private WaitlistEntry obtenerEntradaA(UUID id) {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return waitlistEntryRepository.findWithPatientByIdAndTenantId(id, tenantA.getId())
          .orElseThrow();
    } finally {
      TenantContext.clear();
    }
  }

  private long contarCitasA() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return appointmentRepository.count();
    } finally {
      TenantContext.clear();
    }
  }

  private UUID readId(MvcResult result) throws Exception {
    JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
    return UUID.fromString(node.get("id").asText());
  }
}
