package com.julio.odentix.odentix_backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
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
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de resiliencia del asistente (FASE10-03): con el proveedor caído
 * (HTTP 500), ambos endpoints responden 200 con plantilla fija y flag, sin
 * propagar el fallo.
 */
@AutoConfigureMockMvc
class AssistantFallbackIntegrationTest extends AbstractIntegrationTest {

  private static HttpServer groqCaido;
  private static int puertoCaido;

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
  private TreatmentPlanRepository treatmentPlanRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private String tokenA;
  private Appointment citaA;

  @BeforeAll
  static void iniciarGroqCaido() throws IOException {
    groqCaido = HttpServer.create(new InetSocketAddress(0), 0);
    groqCaido.createContext("/chat/completions", intercambio -> {
      byte[] bytes = "{\"error\":{\"message\":\"caído\"}}".getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(500, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    groqCaido.start();
    puertoCaido = groqCaido.getAddress().getPort();
  }

  @AfterAll
  static void detenerGroqCaido() {
    if (groqCaido != null) {
      groqCaido.stop(0);
    }
  }

  @DynamicPropertySource
  static void groqCaido(DynamicPropertyRegistry registry) {
    registry.add("odentix.assistant.groq.api-base-url",
        () -> "http://localhost:" + puertoCaido);
    registry.add("odentix.assistant.groq.api-key", () -> "key-falsa");
    registry.add("odentix.assistant.groq.model", () -> "modelo-falso");
  }

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Fallback Alfa", "9K0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@fallback-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient paciente = patientRepository.save(new Patient(tenantA.getId(), "Caído", "Proveedor"));
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantA.getId(), "Dr. Caído"));
      Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.HOURS);
      citaA = appointmentRepository.saveAndFlush(new Appointment(tenantA.getId(), paciente,
          profesional, inicio, inicio.plus(1, ChronoUnit.HOURS)));

      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), paciente);
      plan.setStatus(TreatmentPlanStatus.presentado);
      plan.setTotalPriceCop(new BigDecimal("450000.00"));
      treatmentPlanRepository.saveAndFlush(plan);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void askConProveedorCaidoDevuelvePlantillaFija() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/assistant/ask")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("question", "¿cómo voy?"))))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode respuesta = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(respuesta.get("fallback").asBoolean()).isTrue();
    // Plantilla con datos reales del tenant, no un error.
    assertThat(respuesta.get("answer").asText()).contains("no disponible");
    assertThat(respuesta.get("answer").asText())
        .contains("Planes de tratamiento sin decidir: 1");
  }

  @Test
  void suggestConProveedorCaidoDevuelvePlantillaFija() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/assistant/suggest-message")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("appointmentId", citaA.getId().toString()))))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode respuesta = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(respuesta.get("fallback").asBoolean()).isTrue();
    assertThat(respuesta.get("message").asText()).contains("Caído Proveedor");
    assertThat(respuesta.get("suggestedChannel").asText()).isEqualTo("whatsapp");
  }
}
