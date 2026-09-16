package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas del endpoint de historia clínica (FASE2-06) contra PostgreSQL real
 * vía Testcontainers, con JWT real (mismo patrón que FASE2-02/03/04).
 *
 * <p>Cubre el DoD: registrar y consultar el historial de entradas clínicas de
 * un paciente específico, y no el de pacientes de otro tenant.
 */
@AutoConfigureMockMvc
class ClinicalRecordIntegrationTest extends AbstractIntegrationTest {

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

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Historia A", "905111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "odontologo@historia-a.com", "ClaveSegura123!", "Odontólogo A", UserRole.odontologo);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Historia B", "906333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "odontologo@historia-b.com", "ClaveSegura456!", "Odontólogo B", UserRole.odontologo);
    tokenB = jwtService.generateToken(userB);
  }

  private String crearPaciente(String token) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("firstName", "María");
    body.put("lastName", "Pérez");
    body.put("documentNumber", "DOC-" + UUID.randomUUID());

    MvcResult result = mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> created = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return created.get("id").toString();
  }

  private String cuerpoEntrada(String motivo, Instant recordedAt) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("chiefComplaint", motivo);
    body.put("anamnesis", "Dolor leve al masticar, sin alergias conocidas");
    body.put("diagnosis", "Caries oclusal en pieza 16");
    body.put("evolution", "Se indica obturación y control en 6 meses");
    if (recordedAt != null) {
      body.put("recordedAt", recordedAt.toString());
    }
    return objectMapper.writeValueAsString(body);
  }

  @Test
  @DisplayName("POST agrega entrada clínica → 201 con Location; GET lista historial ordenado desc")
  void agregarYListarHistorialOrdenado() throws Exception {
    String patientId = crearPaciente(tokenA);

    Instant masAntiguo = Instant.now().minusSeconds(3600);
    Instant masReciente = Instant.now();

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoEntrada("Control general y limpieza", masReciente)))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.patientId").value(patientId))
        .andExpect(jsonPath("$.chiefComplaint").value("Control general y limpieza"))
        .andExpect(jsonPath("$.recordedAt").exists());

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoEntrada("Dolor en muela superior derecha", masAntiguo)))
        .andExpect(status().isCreated());

    MvcResult listado = mockMvc.perform(get("/api/v1/patients/" + patientId + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();

    Map<?, ?>[] entradas = objectMapper.readValue(
        listado.getResponse().getContentAsString(), Map[].class);
    assertThat(entradas).hasSize(2);
    // Orden descendente por recordedAt: la más reciente primero.
    assertThat(entradas[0].get("chiefComplaint")).isEqualTo("Control general y limpieza");
    assertThat(entradas[1].get("chiefComplaint")).isEqualTo("Dolor en muela superior derecha");
  }

  @Test
  @DisplayName("Sin motivo de consulta → 400 con el campo que falló")
  void postSinMotivoDevuelve400() throws Exception {
    String patientId = crearPaciente(tokenA);

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"anamnesis\": \"sin motivo\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.chiefComplaint").exists());
  }

  @Test
  @DisplayName("Cross-tenant: POST y GET sobre paciente ajeno → 404 y datos invisibles")
  void aislamientoCrossTenant() throws Exception {
    String patientIdB = crearPaciente(tokenB);

    mockMvc.perform(post("/api/v1/patients/" + patientIdB + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoEntrada("Intento cross-tenant", null)))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + patientIdB + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    // El paciente ajeno quedó intacto: sin entradas clínicas creadas.
    mockMvc.perform(get("/api/v1/patients/" + patientIdB + "/clinical-records")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("Paciente inactivo → 404 para la API (baja lógica)")
  void pacienteInactivoNoAceptaEntradas() throws Exception {
    String patientId = crearPaciente(tokenA);

    mockMvc.perform(delete("/api/v1/patients/" + patientId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/clinical-records")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(cuerpoEntrada("Paciente dado de baja", null)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("Sin token → 401")
  void sinTokenDevuelve401() throws Exception {
    String patientId = crearPaciente(tokenA);

    mockMvc.perform(get("/api/v1/patients/" + patientId + "/clinical-records"))
        .andExpect(status().isUnauthorized());
  }
}
