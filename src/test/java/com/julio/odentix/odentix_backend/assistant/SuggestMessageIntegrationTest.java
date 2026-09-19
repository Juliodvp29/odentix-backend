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
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * Pruebas de generación asistida de mensajes (FASE10-02) con proveedor falso
 * local.
 *
 * <p>Cubre el DoD del ticket: el endpoint devuelve el mensaje sugerido
 * <b>sin efectos secundarios</b> (cero filas en `notifications` y `tasks`),
 * con canal sugerido según contacto, hint incluido y 404 ante cita ajena.
 */
@AutoConfigureMockMvc
class SuggestMessageIntegrationTest extends AbstractIntegrationTest {

  private static final String MENSAJE_FALSO = "Hola, te recordamos tu cita de mañana.";

  private static HttpServer groqFalso;
  private static int puertoFalso;
  private static final AtomicReference<String> cuerpoEnviado = new AtomicReference<>();

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
  private NotificationRepository notificationRepository;

  @Autowired
  private TaskRepository taskRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private Appointment citaA;
  private Appointment citaB;

  @BeforeAll
  static void iniciarGroqFalso() throws IOException {
    groqFalso = HttpServer.create(new InetSocketAddress(0), 0);
    groqFalso.createContext("/chat/completions", intercambio -> {
      cuerpoEnviado.set(new String(
          intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String json = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\""
          + MENSAJE_FALSO + "\"}}]}";
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

    tenantA = tenantRepository.save(new Tenant("Clínica Mensaje Alfa", "9J0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@mensaje-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Mensaje Beta", "9J0133444-2"));
    userService.createUser(
        tenantB.getId(), "recepcion@mensaje-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);

    citaA = sembrarCita(tenantA.getId(), "Sugiere", "Nada", "573007778899");
    citaB = sembrarCita(tenantB.getId(), "Ajena", "Beta", "573008889900");
  }

  private Appointment sembrarCita(UUID tenantId, String nombre, String apellido, String telefono) {
    TenantContext.setTenantId(tenantId);
    try {
      Patient paciente = patientRepository.save(new Patient(tenantId, nombre, apellido));
      paciente.setPhone(telefono);
      paciente = patientRepository.save(paciente);
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantId, "Dr. Mensaje"));
      Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.HOURS);
      return appointmentRepository.saveAndFlush(new Appointment(tenantId, paciente, profesional,
          inicio, inicio.plus(1, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode sugerir(String token, UUID citaId, String hint) throws Exception {
    Map<String, Object> body = new HashMap<>();
    if (citaId != null) {
      body.put("appointmentId", citaId.toString());
    }
    if (hint != null) {
      body.put("hint", hint);
    }
    MvcResult result = mockMvc.perform(post("/api/v1/assistant/suggest-message")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private long contar(UUID tenantId,
      java.util.function.Function<UUID, java.util.List<?>> lector) {
    TenantContext.setTenantId(tenantId);
    try {
      return lector.apply(tenantId).size();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void sugiereMensajeSinEfectosSecundarios() throws Exception {
    JsonNode respuesta =
        sugerir(tokenA, citaA.getId(), "tono urgente, cita sin confirmar");

    assertThat(respuesta.get("message").asText()).isEqualTo(MENSAJE_FALSO);
    assertThat(respuesta.get("suggestedChannel").asText()).isEqualTo("whatsapp");
    assertThat(respuesta.get("model").asText()).isEqualTo("modelo-falso");

    // El hint y los datos de la cita llegaron al proveedor…
    assertThat(cuerpoEnviado.get()).contains("tono urgente");
    assertThat(cuerpoEnviado.get()).contains("Sugiere Nada");

    // …pero no se envió ni registró nada.
    assertThat(contar(tenantA.getId(), t -> notificationRepository.findAll())).isZero();
    assertThat(contar(tenantA.getId(), t -> taskRepository.findAll())).isZero();
  }

  @Test
  void citaDeOtroTenantDevuelve404() throws Exception {
    Map<String, Object> body = Map.of("appointmentId", citaB.getId().toString());
    mockMvc.perform(post("/api/v1/assistant/suggest-message")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }
}
