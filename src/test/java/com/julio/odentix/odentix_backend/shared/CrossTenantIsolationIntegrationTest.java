package com.julio.odentix.odentix_backend.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.controller.TestBusinessEntityController;
import com.julio.odentix.odentix_backend.shared.controller.TestBusinessEntityController.UpdateTestEntityRequest;
import com.julio.odentix.odentix_backend.shared.entity.TestBusinessEntity;
import com.julio.odentix.odentix_backend.shared.repository.TestBusinessEntityRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test crítico de aislamiento cross-tenant (FASE1-10).
 *
 * <p>Verifica que un tenant bajo ninguna circunstancia pueda ver, modificar o inferir la
 * existencia de registros pertenecientes a otro tenant a través de la capa REST y repositorios.
 *
 * <p>Regla crítica (aislamiento multi-tenant del proyecto):
 * Las respuestas a intentos de acceso cross-tenant por ID deben ser 404 (Not Found)
 * y NUNCA 403 (Forbidden), evitando revelar la existencia de recursos ajenos.
 */
@AutoConfigureMockMvc
@Import(TestBusinessEntityController.class)
class CrossTenantIsolationIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private TestBusinessEntityRepository testBusinessEntityRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Tenant tenantA;
  private Tenant tenantB;

  private String tokenTenantA;
  private String tokenTenantB;

  private TestBusinessEntity entityTenantA;
  private TestBusinessEntity entityTenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    testBusinessEntityRepository.deleteAll();

    // 1. Crear Tenant A y su usuario con token
    tenantA = tenantRepository.save(new Tenant("Clínica Alpha", "901111222-1"));
    User userA = userService.createUser(
        tenantA.getId(),
        "propietario@alpha.com",
        "ClaveSegura123!",
        "Dr. Propietario Alpha",
        UserRole.propietario
    );
    tokenTenantA = jwtService.generateToken(userA);

    // 2. Crear Tenant B y su usuario con token
    tenantB = tenantRepository.save(new Tenant("Clínica Beta", "902222333-2"));
    User userB = userService.createUser(
        tenantB.getId(),
        "propietario@beta.com",
        "ClaveSegura456!",
        "Dr. Propietario Beta",
        UserRole.propietario
    );
    tokenTenantB = jwtService.generateToken(userB);

    // 3. Crear entidad perteneciente al Tenant A
    TenantContext.setTenantId(tenantA.getId());
    try {
      entityTenantA = testBusinessEntityRepository.save(new TestBusinessEntity("Servicio Alpha"));
    } finally {
      TenantContext.clear();
    }

    // 4. Crear entidad perteneciente al Tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      entityTenantB = testBusinessEntityRepository.save(new TestBusinessEntity("Servicio Confidencial Beta"));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("1. Acceso legítimo: Tenant A lee y modifica su propia entidad con éxito (200 OK)")
  void tenantAPuedeLeerYModificarSuPropiaEntidad() throws Exception {
    // Lectura legítima
    mockMvc.perform(get("/api/v1/test-entities/" + entityTenantA.getId())
            .header("Authorization", "Bearer " + tokenTenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(entityTenantA.getId().toString()))
        .andExpect(jsonPath("$.name").value("Servicio Alpha"))
        .andExpect(jsonPath("$.tenantId").value(tenantA.getId().toString()));

    // Modificación legítima
    UpdateTestEntityRequest updateRequest = new UpdateTestEntityRequest("Servicio Alpha Actualizado");
    mockMvc.perform(put("/api/v1/test-entities/" + entityTenantA.getId())
            .header("Authorization", "Bearer " + tokenTenantA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(entityTenantA.getId().toString()))
        .andExpect(jsonPath("$.name").value("Servicio Alpha Actualizado"));

    // Verificar en BD que el cambio ocurrió
    TenantContext.setTenantId(tenantA.getId());
    try {
      TestBusinessEntity updated = testBusinessEntityRepository.findById(entityTenantA.getId()).orElseThrow();
      assertThat(updated.getName()).isEqualTo("Servicio Alpha Actualizado");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("2. Lectura cross-tenant: Tenant A intenta leer entidad de Tenant B por ID directo -> 404 Not Found")
  void tenantANoPuedeLeerEntidadDeTenantBDevuelve404() throws Exception {
    // Tenant A intenta consultar la entidad de Tenant B conociendo su UUID exacto
    mockMvc.perform(get("/api/v1/test-entities/" + entityTenantB.getId())
            .header("Authorization", "Bearer " + tokenTenantA))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("3. Modificación cross-tenant: Tenant A intenta PUT sobre entidad de Tenant B -> 404 y datos de B intactos")
  void tenantANoPuedeModificarEntidadDeTenantBDevuelve404YNoAlteraDatos() throws Exception {
    UpdateTestEntityRequest attackRequest = new UpdateTestEntityRequest("Intento de Hack Cross-Tenant");

    // Intento de actualización por parte de Tenant A sobre recurso de Tenant B
    mockMvc.perform(put("/api/v1/test-entities/" + entityTenantB.getId())
            .header("Authorization", "Bearer " + tokenTenantA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(attackRequest)))
        .andExpect(status().isNotFound());

    // Verificar en BD que los datos de Tenant B permanecen inalterados
    TenantContext.setTenantId(tenantB.getId());
    try {
      TestBusinessEntity unaffected = testBusinessEntityRepository.findById(entityTenantB.getId()).orElseThrow();
      assertThat(unaffected.getName()).isEqualTo("Servicio Confidencial Beta");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("4. Listado masivo: Tenant A llama findAll y solo ve sus datos (cero registros de Tenant B)")
  void tenantALlamaFindAllYSoloVeSusPropiosRegistros() throws Exception {
    mockMvc.perform(get("/api/v1/test-entities")
            .header("Authorization", "Bearer " + tokenTenantA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(entityTenantA.getId().toString()))
        .andExpect(jsonPath("$[0].name").value("Servicio Alpha"))
        .andExpect(jsonPath("$[0].tenantId").value(tenantA.getId().toString()));

    // Verificar también para Tenant B
    mockMvc.perform(get("/api/v1/test-entities")
            .header("Authorization", "Bearer " + tokenTenantB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(entityTenantB.getId().toString()))
        .andExpect(jsonPath("$[0].name").value("Servicio Confidencial Beta"))
        .andExpect(jsonPath("$[0].tenantId").value(tenantB.getId().toString()));
  }

  @Test
  @DisplayName("5. Sin autenticación: petición sin token JWT -> 401 Unauthorized")
  void requestSinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/test-entities/" + entityTenantA.getId()))
        .andExpect(status().isUnauthorized());
  }
}

