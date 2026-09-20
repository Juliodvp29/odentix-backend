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
 * Pruebas de los endpoints de odontograma (FASE2-08) contra PostgreSQL real
 * vía Testcontainers, con JWT real (mismo patrón que FASE2-06).
 *
 * <p>Cubre el DoD: el GET distingue estado actual, diagnóstico, plan y
 * tratamiento realizado sin que el consumidor infiera nada, más el
 * aislamiento cross-tenant verificado con test.
 */
@AutoConfigureMockMvc
class OdontogramIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Odontograma API A", "909111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "odontologo@odonto-a.com", "ClaveSegura123!", "Odontólogo A", UserRole.odontologo);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Odontograma API B", "910333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "odontologo@odonto-b.com", "ClaveSegura456!", "Odontólogo B", UserRole.odontologo);
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

  private Map<String, Object> cuerpoEntrada(
      int toothNumber, String entryType, String condition, Instant recordedAt) {
    Map<String, Object> body = new HashMap<>();
    body.put("toothNumber", toothNumber);
    body.put("surface", "oclusal");
    body.put("entryType", entryType);
    body.put("condition", condition);
    body.put("notes", "Nota de prueba");
    if (recordedAt != null) {
      body.put("recordedAt", recordedAt.toString());
    }
    return body;
  }

  private String postEntrada(String token, String patientId, Map<String, Object> body) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> created = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return created.get("id").toString();
  }

  @Test
  @DisplayName("POST → 201 con Location; GET agrupa por pieza y por tipo con los cuatro tipos")
  void postYGetAgrupado() throws Exception {
    String patientId = crearPaciente(tokenA);

    postEntrada(tokenA, patientId, cuerpoEntrada(26, "estado_actual", "sano", null));
    postEntrada(tokenA, patientId, cuerpoEntrada(16, "diagnostico", "caries", null));
    postEntrada(tokenA, patientId, cuerpoEntrada(16, "plan_propuesto", "obturación", null));

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(16, "tratamiento_realizado", "obturado", null))))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.patientId").value(patientId))
        .andExpect(jsonPath("$.toothNumber").value(16))
        .andExpect(jsonPath("$.entryType").value("tratamiento_realizado"))
        .andExpect(jsonPath("$.recordedBy").doesNotExist());

    // Piezas ordenadas numéricamente; cada una con los cuatro tipos explícitos.
    mockMvc.perform(get("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patientId").value(patientId))
        .andExpect(jsonPath("$.teeth.length()").value(2))
        .andExpect(jsonPath("$.teeth[0].toothNumber").value(16))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico.length()").value(1))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico[0].condition").value("caries"))
        .andExpect(jsonPath("$.teeth[0].entries.plan_propuesto.length()").value(1))
        .andExpect(jsonPath("$.teeth[0].entries.tratamiento_realizado.length()").value(1))
        .andExpect(jsonPath("$.teeth[0].entries.estado_actual.length()").value(0))
        .andExpect(jsonPath("$.teeth[1].toothNumber").value(26))
        .andExpect(jsonPath("$.teeth[1].entries.estado_actual.length()").value(1))
        .andExpect(jsonPath("$.teeth[1].entries.diagnostico.length()").value(0));
  }

  @Test
  @DisplayName("Varias entradas del mismo tipo no se sobrescriben y van por fecha desc")
  void mismoTipoCoexisteOrdenado() throws Exception {
    String patientId = crearPaciente(tokenA);

    postEntrada(tokenA, patientId,
        cuerpoEntrada(16, "diagnostico", "caries inicial", Instant.now().minusSeconds(3600)));
    postEntrada(tokenA, patientId,
        cuerpoEntrada(16, "diagnostico", "caries avanzada", Instant.now()));

    MvcResult listado = mockMvc.perform(get("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teeth.length()").value(1))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico.length()").value(2))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico[0].condition").value("caries avanzada"))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico[1].condition").value("caries inicial"))
        .andReturn();

    Map<?, ?> odontogram = objectMapper.readValue(
        listado.getResponse().getContentAsString(), Map.class);
    assertThat(odontogram.get("patientId").toString()).isEqualTo(patientId);
  }

  @Test
  @DisplayName("Pieza inválida, tipo ausente/inválido o condición vacía → 400")
  void validacionDevuelve400() throws Exception {
    String patientId = crearPaciente(tokenA);

    // 19 pasa el CHECK 11–48 de BD pero no es FDI permanente.
    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(19, "diagnostico", "caries", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.toothNumber").exists());

    Map<String, Object> sinTipo = cuerpoEntrada(16, null, "caries", null);
    sinTipo.remove("entryType");
    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(sinTipo)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.entryType").exists());

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(16, "diagnostico", "  ", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.condition").exists());

    // Enum inexistente: JSON válido pero valor desconocido → 400, no 500.
    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(16, "inexistente", "caries", null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Cross-tenant: POST y GET sobre paciente ajeno → 404 y datos intactos")
  void aislamientoCrossTenant() throws Exception {
    String patientIdB = crearPaciente(tokenB);
    postEntrada(tokenB, patientIdB, cuerpoEntrada(16, "diagnostico", "caries", null));

    mockMvc.perform(post("/api/v1/patients/" + patientIdB + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(16, "diagnostico", "ataque", null))))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + patientIdB + "/odontogram")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + patientIdB + "/odontogram")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teeth.length()").value(1))
        .andExpect(jsonPath("$.teeth[0].entries.diagnostico[0].condition").value("caries"));
  }

  @Test
  @DisplayName("Mismo tenant, otro paciente: historiales no se mezclan")
  void aislamientoEntrePacientes() throws Exception {
    String patientId1 = crearPaciente(tokenA);
    String patientId2 = crearPaciente(tokenA);
    postEntrada(tokenA, patientId2, cuerpoEntrada(16, "diagnostico", "caries", null));

    mockMvc.perform(get("/api/v1/patients/" + patientId1 + "/odontogram")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.patientId").value(patientId1))
        .andExpect(jsonPath("$.teeth.length()").value(0));
  }

  @Test
  @DisplayName("Paciente inactivo → 404; sin token → 401")
  void inactivoYsinToken() throws Exception {
    String patientId = crearPaciente(tokenA);

    User propietarioA = userService.createUser(
        tenantA.getId(), "prop+" + UUID.randomUUID() + "@odonto-a.com", "ClaveSegura123!", "Propietario A", UserRole.propietario);
    String tokenPropietarioA = jwtService.generateToken(propietarioA);

    mockMvc.perform(delete("/api/v1/patients/" + patientId)
            .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/patients/" + patientId + "/odontogram")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(cuerpoEntrada(16, "diagnostico", "caries", null))))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + patientId + "/odontogram"))
        .andExpect(status().isUnauthorized());
  }
}

