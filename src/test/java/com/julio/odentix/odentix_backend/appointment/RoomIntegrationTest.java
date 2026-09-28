package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
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
 * Pruebas de integración HTTP para el listado de consultorios
 * ({@code GET /api/v1/rooms}) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre: listado ordenado por nombre con sus campos, legible por los
 * roles de agenda, y aislamiento cross-tenant (regla de aislamiento
 * multi-tenant del proyecto).
 */
@AutoConfigureMockMvc
class RoomIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private RoomRepository roomRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenPropietarioB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Consultorios Alfa", "970111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@rooms-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@rooms-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Consultorios Beta", "971333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@rooms-beta.com", "ClaveSegura456!", "Propietario Beta",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      roomRepository.saveAndFlush(new Room(tenantA.getId(), "Consultorio 1"));
      roomRepository.saveAndFlush(new Room(tenantA.getId(), "Consultorio 2"));
      Room inactivo = new Room(tenantA.getId(), "Bodega");
      inactivo.setActive(false);
      roomRepository.saveAndFlush(inactivo);
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      roomRepository.saveAndFlush(new Room(tenantB.getId(), "Box Beta"));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void listarDevuelveOrdenadosPorNombreConCampos() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/rooms")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(3);
    assertThat(lista.get(0).get("name").asText()).isEqualTo("Bodega");
    assertThat(lista.get(1).get("name").asText()).isEqualTo("Consultorio 1");
    assertThat(lista.get(2).get("name").asText()).isEqualTo("Consultorio 2");

    JsonNode primero = lista.get(0);
    assertThat(primero.get("id").asText()).isNotBlank();
    assertThat(primero.get("isActive").asBoolean()).isFalse();
    assertThat(lista.get(1).get("isActive").asBoolean()).isTrue();
  }

  @Test
  void recepcionPuedeListarParaElSelector() throws Exception {
    mockMvc.perform(
            get("/api/v1/rooms")
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk());
  }

  @Test
  void cadaTenantSoloVeSusConsultorios() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/rooms")
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(lista).hasSize(1);
    assertThat(lista.get(0).get("name").asText()).isEqualTo("Box Beta");
  }
}
