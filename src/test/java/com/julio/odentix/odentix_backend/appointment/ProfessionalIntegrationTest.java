package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para profesionales (listado y creación)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre:
 * <ul>
 *   <li>Listado ordenado por nombre con filtros {@code onlyActive} y
 *       {@code externalOnly}, legible por los roles de agenda.</li>
 *   <li>Creación reservada al propietario (recepción → 403).</li>
 *   <li>Aislamiento cross-tenant: cada tenant solo ve sus profesionales
 *       (regla de aislamiento multi-tenant del proyecto).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class ProfessionalIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private ProfessionalRepository professionalRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenPropietarioB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Profesionales Alfa", "950111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@prof-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@prof-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Profesionales Beta", "951333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@prof-beta.com", "ClaveSegura456!", "Propietario Beta",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Professional externa = new Professional(tenantA.getId(), "Ana Externa");
      externa.setExternal(true);
      externa.setSpecialty("Ortodoncia");
      externa.setLicenseNumber("LIC-ANA-1");
      professionalRepository.saveAndFlush(externa);

      professionalRepository.saveAndFlush(
          new Professional(tenantA.getId(), "Bruno Planta"));

      Professional inactiva = new Professional(tenantA.getId(), "Carla Inactiva");
      inactiva.setActive(false);
      professionalRepository.saveAndFlush(inactiva);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      professionalRepository.saveAndFlush(
          new Professional(tenantB.getId(), "Beto Beta"));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Listado
  // ---------------------------------------------------------------------------

  @Test
  void listarDevuelveOrdenadosPorNombreConCampos() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(3);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Ana Externa");
    assertThat(lista.get(1).get("fullName").asText()).isEqualTo("Bruno Planta");
    assertThat(lista.get(2).get("fullName").asText()).isEqualTo("Carla Inactiva");

    JsonNode primera = lista.get(0);
    assertThat(primera.get("id").asText()).isNotBlank();
    assertThat(primera.get("specialty").asText()).isEqualTo("Ortodoncia");
    assertThat(primera.get("licenseNumber").asText()).isEqualTo("LIC-ANA-1");
    assertThat(primera.get("isExternal").asBoolean()).isTrue();
    assertThat(primera.get("isActive").asBoolean()).isTrue();
    assertThat(lista.get(2).get("isActive").asBoolean()).isFalse();
  }

  @Test
  void filtroOnlyActiveExcluyeInactivos() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/professionals").queryParam("onlyActive", "true")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Ana Externa");
    assertThat(lista.get(1).get("fullName").asText()).isEqualTo("Bruno Planta");
  }

  @Test
  void filtroExternalOnlyDevuelveSoloExternos() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/professionals").queryParam("externalOnly", "true")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Ana Externa");
  }

  @Test
  void recepcionPuedeListarParaElSelector() throws Exception {
    mockMvc.perform(
            get("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk());
  }

  @Test
  void cadaTenantSoloVeSusProfesionales() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Beto Beta");
  }

  // ---------------------------------------------------------------------------
  // Creación
  // ---------------------------------------------------------------------------

  @Test
  void crearProfesionalDevuelve201YApareceEnListado() throws Exception {
    Map<String, Object> body = Map.of(
        "fullName", "Diana Nueva",
        "specialty", "Endodoncia",
        "licenseNumber", "LIC-DIANA-9",
        "isExternal", true);

    MvcResult result = mockMvc.perform(
            post("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andReturn();
    JsonNode creada = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(creada.get("id").asText()).isNotBlank();
    assertThat(creada.get("fullName").asText()).isEqualTo("Diana Nueva");
    assertThat(creada.get("specialty").asText()).isEqualTo("Endodoncia");
    assertThat(creada.get("isExternal").asBoolean()).isTrue();
    assertThat(creada.get("isActive").asBoolean()).isTrue();

    // Visible en el listado del tenant.
    MvcResult listado = mockMvc.perform(
            get("/api/v1/professionals").queryParam("externalOnly", "true")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode externas = objectMapper.readTree(listado.getResponse().getContentAsString());
    assertThat(externas).hasSize(2);
  }

  @Test
  void crearSinFullNameDevuelve400() throws Exception {
    Map<String, Object> body = Map.of("specialty", "Endodoncia");
    mockMvc.perform(
            post("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recepcionNoPuedeCrearProfesionalDevuelve403() throws Exception {
    Map<String, Object> body = Map.of("fullName", "Dr. Intruso");
    mockMvc.perform(
            post("/api/v1/professionals")
                .header("Authorization", "Bearer " + tokenRecepcionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }
}
