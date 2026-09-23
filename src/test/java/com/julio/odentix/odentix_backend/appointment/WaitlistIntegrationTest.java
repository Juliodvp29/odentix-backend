package com.julio.odentix.odentix_backend.appointment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateWaitlistStatusRequest;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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

  @Autowired
  private WaitlistEntryRepository waitlistEntryRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private String tokenEspecialistaA;
  private Patient patientA;
  private Patient patientBusqueda;
  private Patient patientB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Espera Alfa", "909111222-1"));
    User userA = userService.createUser(
        tenantA.getId(),
        "recepcion@espera-alfa.com",
        "ClaveSegura123!",
        "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(),
        "externo@espera-alfa.com",
        "ClaveSegura789!",
        "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Espera Beta", "910333444-2"));
    User userB = userService.createUser(
        tenantB.getId(),
        "recepcion@espera-beta.com",
        "ClaveSegura456!",
        "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient ana = new Patient(tenantA.getId(), "Ana María", "Torres");
      ana.setPhone("3001112233");
      patientA = patientRepository.save(ana);
      Patient sofia = new Patient(tenantA.getId(), "Sofía", "Restrepo");
      sofia.setPhone("3002223344");
      patientBusqueda = patientRepository.save(sofia);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient beto = new Patient(tenantB.getId(), "Beto", "Pérez");
      beto.setPhone("3009998877");
      patientB = patientRepository.save(beto);
    } finally {
      TenantContext.clear();
    }
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
        .andExpect(jsonPath("$.patientName").value("Ana María Torres"))
        .andExpect(jsonPath("$.procedureId").value(procedureId.toString()))
        .andExpect(jsonPath("$.desiredFrom").value(request.getDesiredFrom().toString()))
        .andExpect(jsonPath("$.desiredTo").value(request.getDesiredTo().toString()))
        .andExpect(jsonPath("$.status").value("activa"))
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        .andExpect(jsonPath("$.contactedAt").doesNotExist())
        .andExpect(jsonPath("$.convertedAt").doesNotExist())
        .andExpect(jsonPath("$.discardedAt").doesNotExist())
        .andExpect(jsonPath("$.convertedAppointmentId").doesNotExist());
  }

  @Test
  void listarUsaPaginacionEstableYExcluyeOtroTenant() throws Exception {
    Instant base = Instant.parse("2030-01-10T08:00:00Z");
    WaitlistEntry antiguo = crearEntrada(patientA, WaitlistStatus.activa, base);
    WaitlistEntry empateUno = crearEntrada(patientA, WaitlistStatus.activa, base.plus(1, ChronoUnit.HOURS));
    WaitlistEntry empateDos = crearEntrada(patientBusqueda, WaitlistStatus.activa, base.plus(1, ChronoUnit.HOURS));
    crearEntrada(patientB, WaitlistStatus.activa, base.plus(2, ChronoUnit.HOURS));

    List<UUID> esperado = List.of(empateUno.getId(), empateDos.getId()).stream()
        .sorted(Comparator.comparing(UUID::toString))
        .toList();

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("page", "0")
            .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.content[0].id").value(esperado.get(0).toString()))
        .andExpect(jsonPath("$.content[1].id").value(esperado.get(1).toString()));

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("size", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[?(@.id == '" + antiguo.getId() + "')]").exists());
  }

  @Test
  void listarRespetaOrdenAscendenteYDesempataPorId() throws Exception {
    Instant base = Instant.parse("2030-01-20T08:00:00Z");
    WaitlistEntry antiguo = crearEntrada(patientA, WaitlistStatus.activa, base);
    WaitlistEntry empateUno = crearEntrada(patientA, WaitlistStatus.activa, base.plus(1, ChronoUnit.HOURS));
    WaitlistEntry empateDos = crearEntrada(patientBusqueda, WaitlistStatus.activa, base.plus(1, ChronoUnit.HOURS));
    List<UUID> esperado = List.of(empateUno.getId(), empateDos.getId()).stream()
        .sorted(Comparator.comparing(UUID::toString))
        .toList();

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("sort", "createdAt,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(antiguo.getId().toString()))
        .andExpect(jsonPath("$.content[1].id").value(esperado.get(0).toString()))
        .andExpect(jsonPath("$.content[2].id").value(esperado.get(1).toString()));
  }

  @Test
  void listarBuscaPorNombreSinAcentosOTelefono() throws Exception {
    crearEntrada(patientA, WaitlistStatus.activa, Instant.parse("2030-02-01T08:00:00Z"));
    crearEntrada(patientBusqueda, WaitlistStatus.activa, Instant.parse("2030-02-02T08:00:00Z"));

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("query", "ana maria"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].patientId").value(patientA.getId().toString()));

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("query", "3002223344"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].patientId").value(patientBusqueda.getId().toString()));
  }

  @Test
  void listarFiltraPorEstadoYExcluyeDatosDeOtroTenant() throws Exception {
    crearEntrada(patientA, WaitlistStatus.activa, Instant.parse("2030-03-01T08:00:00Z"));
    WaitlistEntry contactada = crearEntrada(
        patientA, WaitlistStatus.contactado, Instant.parse("2030-03-02T08:00:00Z"));
    crearEntrada(patientB, WaitlistStatus.contactado, Instant.parse("2030-03-03T08:00:00Z"));

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("status", "contactado"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].id").value(contactada.getId().toString()));

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenA)
            .param("query", "Pérez"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void consultarPorIdDevuelve404ParaOtroTenant() throws Exception {
    WaitlistEntry entryA = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());

    mockMvc.perform(get("/api/v1/waitlist/{id}", entryA.getId())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(entryA.getId().toString()));

    mockMvc.perform(get("/api/v1/waitlist/{id}", entryA.getId())
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
  }

  @Test
  void cambiarActivaAContactadoFijaContactedAt() throws Exception {
    WaitlistEntry entry = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());
    UpdateWaitlistStatusRequest request = new UpdateWaitlistStatusRequest(WaitlistStatus.contactado);

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entry.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("contactado"))
        .andExpect(jsonPath("$.contactedAt").isNotEmpty());
  }

  @Test
  void cambiarActivaADescartadaGuardaMotivoYTimestamp() throws Exception {
    WaitlistEntry entry = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());
    UpdateWaitlistStatusRequest request = new UpdateWaitlistStatusRequest(WaitlistStatus.descartada);
    request.setDiscardReason("El paciente ya no desea el procedimiento");

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entry.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("descartada"))
        .andExpect(jsonPath("$.discardedAt").isNotEmpty())
        .andExpect(jsonPath("$.discardReason").value("El paciente ya no desea el procedimiento"));
  }

  @Test
  void cambiarContactadaADescartadaConservaHistorial() throws Exception {
    WaitlistEntry entry = crearEntrada(patientBusqueda, WaitlistStatus.contactado, Instant.now());
    entry.setContactedAt(Instant.parse("2030-04-01T08:00:00Z"));
    waitlistEntryRepository.saveAndFlush(entry);
    UpdateWaitlistStatusRequest request = new UpdateWaitlistStatusRequest(WaitlistStatus.descartada);

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entry.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("descartada"))
        .andExpect(jsonPath("$.contactedAt").value("2030-04-01T08:00:00Z"))
        .andExpect(jsonPath("$.discardedAt").isNotEmpty());
  }

  @Test
  void mismoEstadoEsIdempotente() throws Exception {
    WaitlistEntry entry = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entry.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateWaitlistStatusRequest(WaitlistStatus.activa))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("activa"))
        .andExpect(jsonPath("$.contactedAt").doesNotExist());
  }

  @Test
  void patchReintentaConvertirYRechazaRetroceder() throws Exception {
    WaitlistEntry activa = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());
    WaitlistEntry contactada = crearEntrada(patientBusqueda, WaitlistStatus.contactado, Instant.now());

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", activa.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateWaitlistStatusRequest(WaitlistStatus.convertida))))
        .andExpect(status().isConflict());

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", contactada.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateWaitlistStatusRequest(WaitlistStatus.activa))))
        .andExpect(status().isConflict());
  }

  @Test
  void estadoDeOtroTenantDevuelve404() throws Exception {
    WaitlistEntry entryA = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entryA.getId())
            .header("Authorization", "Bearer " + tokenB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateWaitlistStatusRequest(WaitlistStatus.contactado))))
        .andExpect(status().isNotFound());
  }

  @Test
  void rolNoAutorizadoNoPuedeListarNiCambiarEstado() throws Exception {
    WaitlistEntry entry = crearEntrada(patientA, WaitlistStatus.activa, Instant.now());

    mockMvc.perform(get("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenEspecialistaA))
        .andExpect(status().isForbidden());

    mockMvc.perform(patch("/api/v1/waitlist/{id}/status", entry.getId())
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new UpdateWaitlistStatusRequest(WaitlistStatus.contactado))))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/waitlist"))
        .andExpect(status().isUnauthorized());
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
  void rolNoAutorizadoDevuelve403AlCrear() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(solicitud(patientA.getId()))))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401AlCrear() throws Exception {
    mockMvc.perform(post("/api/v1/waitlist")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(solicitud(patientA.getId()))))
        .andExpect(status().isUnauthorized());
  }

  private CreateWaitlistEntryRequest solicitud(UUID patientId) {
    Instant from = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(1, ChronoUnit.DAYS);
    return new CreateWaitlistEntryRequest(
        patientId, UUID.randomUUID(), from, from.plus(7, ChronoUnit.DAYS));
  }

  private WaitlistEntry crearEntrada(Patient patient, WaitlistStatus status, Instant createdAt) {
    TenantContext.setTenantId(patient.getTenantId());
    try {
      WaitlistEntry entry = new WaitlistEntry(patient.getTenantId(), patient);
      entry.setStatus(status);
      entry.setCreatedAt(createdAt);
      entry.setUpdatedAt(createdAt);
      return waitlistEntryRepository.saveAndFlush(entry);
    } finally {
      TenantContext.clear();
    }
  }
}
