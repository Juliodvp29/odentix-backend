package com.julio.odentix.odentix_backend.treatmentplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
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
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanItemRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanStatusRequest;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para los endpoints REST y transiciones de TreatmentPlan (FASE4-02).
 */
@AutoConfigureMockMvc
class TreatmentPlanIntegrationTest extends AbstractIntegrationTest {

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
  private TreatmentPlanRepository treatmentPlanRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenOdontologoA;
  private String tokenRecepcionA;
  private String tokenOdontologoB;
  private Patient patientA;
  private Patient patientB;
  private Professional professionalA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Planes Alfa", "901555444-1"));
    User odontologoA = userService.createUser(
        tenantA.getId(), "odontologo@alfa.com", "ClaveSegura123!", "Dr. Alfa", UserRole.odontologo);
    tokenOdontologoA = jwtService.generateToken(odontologoA);

    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Planes Beta", "902666777-2"));
    User odontologoB = userService.createUser(
        tenantB.getId(), "odontologo@beta.com", "ClaveSegura456!", "Dr. Beta", UserRole.odontologo);
    tokenOdontologoB = jwtService.generateToken(odontologoB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Carlos", "Pérez"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Mario Bros"));
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

  @Test
  void crearPlanDeTratamientoValidoExitoso() throws Exception {
    CreateTreatmentPlanItemRequest item1 = new CreateTreatmentPlanItemRequest(
        null, (short) 16, new BigDecimal("150000.00"), new BigDecimal("10000.00"));
    CreateTreatmentPlanItemRequest item2 = new CreateTreatmentPlanItemRequest(
        null, (short) 21, new BigDecimal("250000.00"), BigDecimal.ZERO);

    CreateTreatmentPlanRequest request = new CreateTreatmentPlanRequest(
        patientA.getId(),
        professionalA.getId(),
        "Plan de rehabilitación estética",
        List.of(item1, item2)
    );

    mockMvc.perform(post("/api/v1/treatment-plans")
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.patientFullName").value("Carlos Pérez"))
        .andExpect(jsonPath("$.professionalId").value(professionalA.getId().toString()))
        .andExpect(jsonPath("$.diagnosis").value("Plan de rehabilitación estética"))
        .andExpect(jsonPath("$.status").value("borrador"))
        .andExpect(jsonPath("$.totalPriceCop").value(390000.00))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].toothNumber").value(16))
        .andExpect(jsonPath("$.items[0].netPriceCop").value(140000.00));
  }

  @Test
  void consultarPlanPorIdConItemsExitoso() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Endodoncia");

    mockMvc.perform(get("/api/v1/treatment-plans/{id}", planId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(planId.toString()))
        .andExpect(jsonPath("$.diagnosis").value("Endodoncia"))
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].toothNumber").value(36));
  }

  @Test
  void listarPlanesConFiltroPorPacienteYEstado() throws Exception {
    crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan 1");
    crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan 2");

    mockMvc.perform(get("/api/v1/treatment-plans")
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .param("patientId", patientA.getId().toString())
            .param("status", "borrador"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void actualizarDiagnosticoEItemsEnBorrador() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Diagnóstico preliminar");

    CreateTreatmentPlanItemRequest nuevoItem = new CreateTreatmentPlanItemRequest(
        null, (short) 11, new BigDecimal("500000.00"), new BigDecimal("50000.00"));
    UpdateTreatmentPlanRequest updateReq = new UpdateTreatmentPlanRequest(
        professionalA.getId(), "Diagnóstico definitivo ajustado", List.of(nuevoItem));

    mockMvc.perform(patch("/api/v1/treatment-plans/{id}", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosis").value("Diagnóstico definitivo ajustado"))
        .andExpect(jsonPath("$.totalPriceCop").value(450000.00))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].toothNumber").value(11));
  }

  @Test
  void impedirModificarItemsSiNoEstaEnBorrador() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan presentado");

    // Avanzamos a presentado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.presentado))))
        .andExpect(status().isOk());

    // Intentamos modificar ítems cuando ya no está en borrador
    CreateTreatmentPlanItemRequest nuevoItem = new CreateTreatmentPlanItemRequest(
        null, (short) 11, new BigDecimal("500000.00"), BigDecimal.ZERO);
    UpdateTreatmentPlanRequest updateReq = new UpdateTreatmentPlanRequest(
        null, "Intento de cambio", List.of(nuevoItem));

    mockMvc.perform(patch("/api/v1/treatment-plans/{id}", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Solo se pueden modificar los procedimientos de un plan en estado 'borrador'")));
  }

  @Test
  void avanzarCicloDeVidaValidoExitoso() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Ortodoncia completa");

    // borrador -> presentado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.presentado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("presentado"))
        .andExpect(jsonPath("$.presentedAt").isNotEmpty())
        .andExpect(jsonPath("$.lastContactAt").isNotEmpty());

    // presentado -> en_decision
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.en_decision))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("en_decision"));

    // en_decision -> aceptado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.aceptado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("aceptado"));

    // aceptado -> en_ejecucion
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.en_ejecucion))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("en_ejecucion"));

    // en_ejecucion -> completado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.completado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completado"));
  }

  @Test
  void rechazarTransicionInvalida() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan nuevo");

    // De borrador no se puede pasar directamente a completado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.completado))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("No se puede pasar de 'borrador' a 'completado'")));
  }

  @Test
  void rechazarTransicionDesdeEstadoFinal() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan a completar");

    // Forzamos el estado a completado
    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = treatmentPlanRepository.findById(planId).orElseThrow();
      plan.setStatus(TreatmentPlanStatus.completado);
      treatmentPlanRepository.saveAndFlush(plan);
    } finally {
      TenantContext.clear();
    }

    // Intentamos salir del estado terminal
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.en_decision))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("es un estado terminal")));
  }

  @Test
  void aislamientoCrossTenantEstricto() throws Exception {
    UUID planId = crearPlanEnDb(tenantA.getId(), patientA, professionalA, "Plan privado Alfa");

    // Usuario del tenant B intenta consultar el plan del tenant A
    mockMvc.perform(get("/api/v1/treatment-plans/{id}", planId)
            .header("Authorization", "Bearer " + tokenOdontologoB))
        .andExpect(status().isNotFound());

    // Usuario del tenant B intenta transicionar el plan del tenant A
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOdontologoB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.presentado))))
        .andExpect(status().isNotFound());
  }

  @Test
  void autorizacionPorRol() throws Exception {
    CreateTreatmentPlanItemRequest item = new CreateTreatmentPlanItemRequest(
        null, (short) 11, new BigDecimal("100000.00"), BigDecimal.ZERO);
    CreateTreatmentPlanRequest request = new CreateTreatmentPlanRequest(
        patientA.getId(), professionalA.getId(), "Plan odontológico", List.of(item));

    // 1. Recepción intenta crear un plan -> 403 Forbidden (solo facultativos)
    mockMvc.perform(post("/api/v1/treatment-plans")
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    // 2. Odontólogo lo crea exitosamente -> 201 Created
    MvcResult result = mockMvc.perform(post("/api/v1/treatment-plans")
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    UUID planId = UUID.fromString(objectMapper.readTree(responseBody).get("id").asText());

    // 3. Recepción avanza el estado a presentado -> 200 OK (permitido para recepcion)
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.presentado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("presentado"));
  }

  private UUID crearPlanEnDb(UUID tenantId, Patient patient, Professional professional, String diagnosis) {
    TenantContext.setTenantId(tenantId);
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantId, patient, professional, diagnosis);
      TreatmentPlanItem item = new TreatmentPlanItem(tenantId, plan, null, (short) 36, new BigDecimal("200000.00"), BigDecimal.ZERO);
      plan.addItem(item);
      return treatmentPlanRepository.saveAndFlush(plan).getId();
    } finally {
      TenantContext.clear();
    }
  }
}
