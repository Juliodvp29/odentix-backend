package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Suite crítica de pruebas de aislamiento cross-tenant para el módulo de pacientes (FASE2-04).
 *
 * <p>Reglas no negociables (aislamiento multi-tenant):
 * <ul>
 *   <li>Ninguna consulta o listado puede exponer datos de pacientes entre clínicas distintas.</li>
 *   <li>Cualquier intento de acceso a un paciente de otro tenant por ID directo DEBE responder
 *       HTTP 404 (Not Found) y NUNCA HTTP 403 (Forbidden), para evitar inferir o confirmar la
 *       existencia de registros de otros tenants.</li>
 *   <li>Las modificaciones y eliminaciones sobre IDs de otros tenants deben ser rechazadas con 404
 *       y dejar los datos del tenant original completamente intactos.</li>
 *   <li>La búsqueda paginada no debe encontrar coincidencias de otros tenants aunque el texto coincida exactamente.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class PatientCrossTenantIsolationIntegrationTest extends AbstractIntegrationTest {

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

    tenantA = tenantRepository.save(new Tenant("Clínica Odontológica Norte", "900100200-1"));
    User userA = userService.createUser(
        tenantA.getId(), "admin@norte.com", "PasswordSegura123!", "Admin Norte", UserRole.propietario);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Odontológica Sur", "900300400-2"));
    User userB = userService.createUser(
        tenantB.getId(), "admin@sur.com", "PasswordSegura456!", "Admin Sur", UserRole.propietario);
    tokenB = jwtService.generateToken(userB);
  }

  private String crearPaciente(String token, String nombre, String apellido, String docNumber) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("firstName", nombre);
    body.put("lastName", apellido);
    body.put("documentType", "CC");
    body.put("documentNumber", docNumber);
    body.put("phone", "3001112233");
    body.put("email", (nombre + "." + apellido + "@correo.com").toLowerCase());

    MvcResult result = mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();

    Map<?, ?> response = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return response.get("id").toString();
  }

  @Test
  @DisplayName("Listado paginado GET /api/v1/patients solo devuelve pacientes del tenant autenticado")
  void listadoPaginadoSoloDevuelvePacientesDelTenantActivo() throws Exception {
    // Crear 2 pacientes en Tenant A y 3 pacientes en Tenant B
    crearPaciente(tokenA, "Ana", "Martínez", "DOC-A-1");
    crearPaciente(tokenA, "Bernardo", "López", "DOC-A-2");

    crearPaciente(tokenB, "Carlos", "Ramírez", "DOC-B-1");
    crearPaciente(tokenB, "Diana", "Castro", "DOC-B-2");
    crearPaciente(tokenB, "Esteban", "Torres", "DOC-B-3");

    // Tenant A lista pacientes → debe ver exactamente 2 pacientes, ambos de Tenant A
    mockMvc.perform(get("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].tenantId").value(tenantA.getId().toString()))
        .andExpect(jsonPath("$.content[1].tenantId").value(tenantA.getId().toString()));

    // Tenant B lista pacientes → debe ver exactamente 3 pacientes, todos de Tenant B
    mockMvc.perform(get("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.content[0].tenantId").value(tenantB.getId().toString()))
        .andExpect(jsonPath("$.content[1].tenantId").value(tenantB.getId().toString()))
        .andExpect(jsonPath("$.content[2].tenantId").value(tenantB.getId().toString()));
  }

  @Test
  @DisplayName("Búsqueda paginada no devuelve pacientes de otro tenant aunque el término coincida exactamente")
  void busquedaPaginadaNoDevuelvePacientesDeOtroTenant() throws Exception {
    // Tenant B tiene un paciente llamado "Valentina Morales" con documento "CC-778899"
    crearPaciente(tokenB, "Valentina", "Morales", "CC-778899");

    // Tenant A busca "Valentina" → no debe encontrar ningún resultado
    mockMvc.perform(get("/api/v1/patients?query=Valentina")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());

    // Tenant A busca por el documento exacto de Tenant B → no debe encontrar ningún resultado
    mockMvc.perform(get("/api/v1/patients?query=778899")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.content").isEmpty());

    // Tenant B busca "Valentina" → sí lo encuentra
    mockMvc.perform(get("/api/v1/patients?query=Valentina")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.content[0].firstName").value("Valentina"))
        .andExpect(jsonPath("$.content[0].tenantId").value(tenantB.getId().toString()));
  }

  @Test
  @DisplayName("Lectura por ID directo de otro tenant devuelve 404 (Not Found), nunca 403")
  void lecturaPorIdDirectoDeOtroTenantDevuelve404() throws Exception {
    String idB = crearPaciente(tokenB, "Gabriel", "Ríos", "DOC-GABRIEL");

    // Tenant A intenta leer el paciente de Tenant B usando su ID exacto
    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.error").value("Not Found"))
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));

    // Tenant B lee su propio paciente → 200 OK
    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(idB))
        .andExpect(jsonPath("$.firstName").value("Gabriel"));
  }

  @Test
  @DisplayName("Modificación PATCH sobre paciente de otro tenant devuelve 404 y datos permanecen intactos")
  void modificacionPatchDeOtroTenantDevuelve404YNoAlteraDatos() throws Exception {
    String idB = crearPaciente(tokenB, "Hugo", "Navarro", "DOC-HUGO");

    // Tenant A intenta modificar los datos de Hugo Navarro
    mockMvc.perform(patch("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"firstName\": \"Atacante\", \"phone\": \"0000000\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    // Verificar que los datos en Tenant B siguen intactos
    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Hugo"))
        .andExpect(jsonPath("$.phone").value("3001112233"));
  }

  @Test
  @DisplayName("Baja lógica DELETE sobre paciente de otro tenant devuelve 404 y estado permanece activo")
  void bajaLogicaDeleteDeOtroTenantDevuelve404YNoAlteraEstado() throws Exception {
    String idB = crearPaciente(tokenB, "Isabel", "Ortiz", "DOC-ISABEL");

    // Tenant A intenta eliminar a Isabel Ortiz
    mockMvc.perform(delete("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404));

    // Verificar en la BD y en la API que el paciente sigue activo en Tenant B
    mockMvc.perform(get("/api/v1/patients/" + idB)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true));

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient enBd = patientRepository.findByIdAndTenantId(UUID.fromString(idB), tenantB.getId())
          .orElseThrow(() -> new AssertionError("El paciente de Tenant B debe existir"));
      assertThat(enBd.isActive()).isTrue();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("Repositorio JPA y queries respetan el aislamiento bajo TenantContext")
  void repositorioJpaRespetaAislamientoBajoTenantContext() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient patientA = new Patient(tenantA.getId(), "Lucía", "Paz");
      patientA.setDocumentNumber("DOC-LUCIA");
      patientRepository.save(patientA);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient patientB = new Patient(tenantB.getId(), "Mateo", "Vargas");
      patientB.setDocumentNumber("DOC-MATEO");
      patientRepository.save(patientB);
    } finally {
      TenantContext.clear();
    }

    // Contexto de Tenant A
    TenantContext.setTenantId(tenantA.getId());
    try {
      List<Patient> listA = patientRepository.findAllByTenantId(tenantA.getId());
      assertThat(listA).extracting(Patient::getFirstName).contains("Lucía").doesNotContain("Mateo");

      Page<Patient> pageA = patientRepository.findAllByTenantIdAndIsActiveTrue(tenantA.getId(), PageRequest.of(0, 10));
      assertThat(pageA.getContent()).extracting(Patient::getFirstName).contains("Lucía").doesNotContain("Mateo");

      Page<Patient> searchA = patientRepository.search(tenantA.getId(), "Mateo", PageRequest.of(0, 10));
      assertThat(searchA.getTotalElements()).isZero();
    } finally {
      TenantContext.clear();
    }
  }
}

