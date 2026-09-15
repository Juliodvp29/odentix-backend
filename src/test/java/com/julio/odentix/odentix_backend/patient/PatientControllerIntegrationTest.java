package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * Pruebas del CRUD de pacientes (FASE2-02) contra PostgreSQL real vía
 * Testcontainers, con JWT real (mismo patrón que
 * CrossTenantIsolationIntegrationTest).
 *
 * <p>Cubre el DoD: POST inválido → 400 con el campo que falló, y DELETE
 * como baja lógica (marca {@code is_active = false} sin borrar la fila).
 */
@AutoConfigureMockMvc
class PatientControllerIntegrationTest extends AbstractIntegrationTest {

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

    tenantA = tenantRepository.save(new Tenant("Clínica CRUD A", "903111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@crud-a.com", "ClaveSegura123!", "Recepción A", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica CRUD B", "904333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@crud-b.com", "ClaveSegura456!", "Recepción B", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);
  }

  private Map<String, Object> pacienteValido() {
    Map<String, Object> body = new HashMap<>();
    body.put("firstName", "María");
    body.put("lastName", "Pérez");
    body.put("documentType", "CC");
    body.put("documentNumber", "DOC-" + UUID.randomUUID());
    body.put("birthDate", "1990-05-15");
    body.put("phone", "3001234567");
    body.put("email", "maria.perez@correo.com");
    body.put("address", "Calle 123 #45-67, Bogotá");
    body.put("emergencyContactName", "Juan Pérez");
    body.put("emergencyContactPhone", "3107654321");
    return body;
  }

  private String crearPaciente(String token) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(pacienteValido())))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return body.get("id").toString();
  }

  @Test
  @DisplayName("POST válido → 201 con Location y el paciente creado")
  void crearPacienteValidoDevuelve201() throws Exception {
    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(pacienteValido())))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
        .andExpect(jsonPath("$.firstName").value("María"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("POST sin nombres → 400 con el detalle del campo que falló")
  void crearPacienteInvalidoDevuelve400ConCampo() throws Exception {
    Map<String, Object> sinNombres = pacienteValido();
    sinNombres.remove("firstName");
    sinNombres.put("email", "no-es-un-email");

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(sinNombres)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.firstName").exists())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  @Test
  @DisplayName("GET propio → 200; GET de otro tenant → 404; inexistente → 404; sin token → 401")
  void lecturaRespetaAislamiento() throws Exception {
    String idA = crearPaciente(tokenA);
    String idB = crearPaciente(tokenB);

    mockMvc.perform(get("/api/v1/patients/" + idA)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(idA))
        .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()))
        .andExpect(jsonPath("$.firstName").value("María"));

    // Cross-tenant por ID directo: 404, no 403 (ni confirma existencia).
    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + UUID.randomUUID())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + idA))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("PATCH parcial actualiza solo los campos enviados")
  void patchParcialActualizaSoloEnviados() throws Exception {
    String id = crearPaciente(tokenA);

    mockMvc.perform(patch("/api/v1/patients/" + id)
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"phone\": \"+573009998877\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phone").value("+573009998877"))
        .andExpect(jsonPath("$.firstName").value("María"));

    mockMvc.perform(patch("/api/v1/patients/" + id)
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\": \"malformado\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors.email").exists());
  }

  @Test
  @DisplayName("PATCH de otro tenant → 404 y datos intactos")
  void patchCrossTenantDevuelve404() throws Exception {
    String idB = crearPaciente(tokenB);

    mockMvc.perform(patch("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\": \"Atacante\"}"))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("María"));
  }

  @Test
  @DisplayName("DELETE → 204, marca is_active = false sin borrar la fila; GET posterior → 404")
  void deleteEsBajaLogica() throws Exception {
    String id = crearPaciente(tokenA);

    mockMvc.perform(delete("/api/v1/patients/" + id)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    // La fila sigue existiendo, con is_active = false.
    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient enBd = patientRepository
          .findByIdAndTenantId(UUID.fromString(id), tenantA.getId())
          .orElseThrow(() -> new AssertionError("La fila no debió borrarse físicamente"));
      assertThat(enBd.isActive()).isFalse();
    } finally {
      TenantContext.clear();
    }

    // Para la API el recurso deja de existir.
    mockMvc.perform(get("/api/v1/patients/" + id)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("DELETE de otro tenant → 404 y el paciente sigue activo")
  void deleteCrossTenantDevuelve404() throws Exception {
    String idB = crearPaciente(tokenB);

    mockMvc.perform(delete("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  @DisplayName("Documento duplicado en el tenant → 409, no 500")
  void documentoDuplicadoDevuelve409() throws Exception {
    Map<String, Object> body = pacienteValido();
    body.put("documentNumber", "DOC-DUP-" + UUID.randomUUID());
    String json = objectMapper.writeValueAsString(body);

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());
  }

  @Test
  @DisplayName("GET /api/v1/patients paginado → 200 con Page estructurado")
  void listarPacientesPaginadoDevuelve200ConEstructuraPage() throws Exception {
    crearPaciente(tokenA);
    crearPaciente(tokenA);

    mockMvc.perform(get("/api/v1/patients?page=0&size=10")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.number").value(0))
        .andExpect(jsonPath("$.content[0].id").exists())
        .andExpect(jsonPath("$.content[0].tenantId").value(tenantA.getId().toString()));
  }

  @Test
  @DisplayName("Búsqueda por fragmento de nombre ignora mayúsculas y acentos (FASE2-03)")
  void busquedaInsensibleAMayusculasYAcentos() throws Exception {
    Map<String, Object> pacienteConAcentos = pacienteValido();
    pacienteConAcentos.put("firstName", "María José");
    pacienteConAcentos.put("lastName", "Gómez Pérez");
    pacienteConAcentos.put("documentNumber", "DOC-ACENTOS-1");

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(pacienteConAcentos)))
        .andExpect(status().isCreated());

    // 1. Buscar "maria" en minúsculas y sin tilde → encuentra "María"
    mockMvc.perform(get("/api/v1/patients?query=maria")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].firstName").value("María José"));

    // 2. Buscar "GOMEZ" en mayúsculas y sin tilde → encuentra "Gómez"
    mockMvc.perform(get("/api/v1/patients?query=GOMEZ")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].lastName").value("Gómez Pérez"));

    // 3. Buscar "perez" sin tilde → encuentra "Pérez"
    mockMvc.perform(get("/api/v1/patients?query=perez")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].lastName").value("Gómez Pérez"));

    // 4. Buscar "jose" sin tilde → encuentra "José"
    mockMvc.perform(get("/api/v1/patients?query=jose")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("Búsqueda por número de documento encuentra el paciente exacto")
  void busquedaPorDocumento() throws Exception {
    Map<String, Object> paciente = pacienteValido();
    String docNumber = "CC-9988776655";
    paciente.put("documentNumber", docNumber);

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(paciente)))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/patients?query=9988776655")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].documentNumber").value(docNumber));
  }

  @Test
  @DisplayName("Búsqueda no incluye pacientes dados de baja (is_active = false)")
  void busquedaNoIncluyePacientesInactivos() throws Exception {
    String id = crearPaciente(tokenA);

    mockMvc.perform(delete("/api/v1/patients/" + id)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/v1/patients?query=María")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  @DisplayName("Recurso inexistente → 404 con formato estándar ApiErrorResponse, sin stacktrace (FASE2-03)")
  void recursoInexistenteDevuelve404ConFormatoEstandar() throws Exception {
    UUID inexistentId = UUID.randomUUID();

    mockMvc.perform(get("/api/v1/patients/" + inexistentId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"))
        .andExpect(jsonPath("$.path").value("/api/v1/patients/" + inexistentId));
  }
}
