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
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
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
import java.util.concurrent.atomic.AtomicReference;
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
 * Pruebas del asistente (FASE10-01) contra PostgreSQL real y un proveedor de
 * IA falso local (cero red real, cero credenciales).
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Una pregunta arma contexto con datos reales del tenant y el cuerpo
 *       enviado al proveedor los contiene (Bearer + modelo + datos).</li>
 *   <li>El contexto nunca mezcla datos de otra clínica.</li>
 *   <li>Sin API key el endpoint responde 502 claro (vía cliente plano en
 *       `GroqChatClientTest`, misma garantía).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class AssistantIntegrationTest extends AbstractIntegrationTest {

  private static final String RESPUESTA_FALSA = "Hay 2 planes sin decidir por $900000.";

  private static HttpServer groqFalso;
  private static int puertoFalso;
  private static final AtomicReference<String> cuerpoEnviado = new AtomicReference<>();
  private static final AtomicReference<String> authEnviado = new AtomicReference<>();

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

  @Autowired
  private InventoryItemRepository inventoryItemRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;

  @BeforeAll
  static void iniciarGroqFalso() throws IOException {
    groqFalso = HttpServer.create(new InetSocketAddress(0), 0);
    groqFalso.createContext("/chat/completions", intercambio -> {
      cuerpoEnviado.set(new String(
          intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      authEnviado.set(intercambio.getRequestHeaders().getFirst("Authorization"));
      String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
          + RESPUESTA_FALSA + "\"}}]}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(200, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    groqFalso.start();
    puertoFalso = groqFalso.getAddress().getPort();
  }

  @AfterAll
  static void detenerGroqFalso() {
    if (groqFalso != null) {
      groqFalso.stop(0);
    }
  }

  @DynamicPropertySource
  static void groqFalso(DynamicPropertyRegistry registry) {
    registry.add("odentix.assistant.groq.api-base-url",
        () -> "http://localhost:" + puertoFalso);
    registry.add("odentix.assistant.groq.api-key", () -> "key-falsa");
    registry.add("odentix.assistant.groq.model", () -> "modelo-falso");
  }

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Asistente Alfa", "9I0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@asistente-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Asistente Beta", "9I0133444-2"));
    userService.createUser(
        tenantB.getId(), "recepcion@asistente-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);

    sembrar(tenantA.getId(), "Paciente Alfa", "Resina Alfa");
    sembrar(tenantB.getId(), "Paciente Beta", "Anestesia Beta");
  }

  private void sembrar(java.util.UUID tenantId, String nombrePaciente, String insumo) {
    TenantContext.setTenantId(tenantId);
    try {
      String[] partes = nombrePaciente.split(" ");
      Patient paciente = patientRepository.save(new Patient(tenantId, partes[0], partes[1]));
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantId, "Dr. IA"));
      Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.HOURS);
      appointmentRepository.saveAndFlush(new Appointment(tenantId, paciente, profesional,
          inicio, inicio.plus(1, ChronoUnit.HOURS)));

      TreatmentPlan plan = new TreatmentPlan(tenantId, paciente);
      plan.setStatus(TreatmentPlanStatus.presentado);
      plan.setTotalPriceCop(new BigDecimal("450000.00"));
      treatmentPlanRepository.saveAndFlush(plan);

      InventoryItem item = new InventoryItem(tenantId, insumo);
      item.setMinThreshold(5);
      inventoryItemRepository.saveAndFlush(item);
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode preguntar(String token, String pregunta) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/assistant/ask")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("question", pregunta))))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void preguntaArmaContextoConDatosRealesDelTenant() throws Exception {
    JsonNode respuesta = preguntar(tokenA, "¿qué tratamientos están pendientes de seguimiento?");

    assertThat(respuesta.get("answer").asText()).isEqualTo(RESPUESTA_FALSA);
    assertThat(respuesta.get("model").asText()).isEqualTo("modelo-falso");

    // Lo enviado al proveedor: auth, modelo, pregunta y datos reales de A.
    assertThat(authEnviado.get()).isEqualTo("Bearer key-falsa");
    assertThat(cuerpoEnviado.get()).contains("modelo-falso");
    assertThat(cuerpoEnviado.get()).contains("¿qué tratamientos están pendientes de seguimiento?");
    assertThat(cuerpoEnviado.get()).contains("Paciente Alfa");
    assertThat(cuerpoEnviado.get()).contains("Resina Alfa");
    assertThat(cuerpoEnviado.get()).contains("Planes de tratamiento sin decidir: 1");
  }

  @Test
  void contextoNuncaMezclaDatosDeOtroTenant() throws Exception {
    preguntar(tokenA, "¿cómo está mi clínica?");

    assertThat(cuerpoEnviado.get()).contains("Paciente Alfa");
    assertThat(cuerpoEnviado.get()).doesNotContain("Paciente Beta");
    assertThat(cuerpoEnviado.get()).doesNotContain("Anestesia Beta");
  }

  @Test
  void preguntaVaciaDevuelve400YSinToken401() throws Exception {
    mockMvc.perform(post("/api/v1/assistant/ask")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("question", "  "))))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/api/v1/assistant/ask")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("question", "hola"))))
        .andExpect(status().isUnauthorized());
  }
}
