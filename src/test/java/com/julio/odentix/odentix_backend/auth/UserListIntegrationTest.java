package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para el listado de miembros del tenant
 * ({@code GET /api/v1/users}) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre: miembros ordenados por nombre con los campos de
 * {@code UserSummaryDto}, legible por los roles operativos, y aislamiento
 * cross-tenant (el tenant sale del token, nunca de un parámetro — regla de
 * aislamiento multi-tenant del proyecto).
 */
@AutoConfigureMockMvc
class UserListIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenPropietarioB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Usuarios Alfa", "980111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@users-alfa.com", "ClaveSegura123!", "Zoe Propietaria",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@users-alfa.com", "ClaveSegura123!", "Ana Recepción",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Usuarios Beta", "981333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@users-beta.com", "ClaveSegura456!", "Beto Propietario",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);
  }

  @Test
  void listarDevuelveMiembrosOrdenadosConCampos() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/users")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Ana Recepción");
    assertThat(lista.get(1).get("fullName").asText()).isEqualTo("Zoe Propietaria");

    JsonNode primera = lista.get(0);
    assertThat(primera.get("id").asText()).isNotBlank();
    assertThat(primera.get("tenantId").asText()).isEqualTo(tenantA.getId().toString());
    assertThat(primera.get("email").asText()).isEqualTo("recepcion@users-alfa.com");
    assertThat(primera.get("role").asText()).isEqualTo("recepcion");
  }

  @Test
  void recepcionPuedeListar() throws Exception {
    mockMvc.perform(
            get("/api/v1/users")
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk());
  }

  @Test
  void cadaTenantSoloVeSusMiembros() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/users")
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).get("fullName").asText()).isEqualTo("Beto Propietario");
    assertThat(lista.get(0).get("tenantId").asText()).isEqualTo(tenantB.getId().toString());
  }
}
