package com.julio.odentix.odentix_backend.saas;

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
 * Pruebas de integración HTTP para el catálogo de planes
 * ({@code GET /api/v1/billing/plans}) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre: tres planes ordenados por precio con sus features y límites
 * desde la BD (el frontend no hardcodea nada), lectura por recepción y
 * 403 para el especialista externo.
 */
@AutoConfigureMockMvc
class PlanCatalogIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private String tokenRecepcion;
  private String tokenEspecialista;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    Tenant tenant = tenantRepository.save(new Tenant("Clínica Catálogo", "9N0111222-1"));
    User recepcion = userService.createUser(
        tenant.getId(), "recepcion@catalogo.com", "ClaveSegura123!", "Recepción",
        UserRole.recepcion);
    tokenRecepcion = jwtService.generateToken(recepcion);
    User especialista = userService.createUser(
        tenant.getId(), "externo@catalogo.com", "ClaveSegura123!", "Ed Externo",
        UserRole.especialista_externo);
    tokenEspecialista = jwtService.generateToken(especialista);
  }

  @Test
  void catalogoTraePlanesOrdenadosConDetalle() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/billing/plans")
                .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode planes = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(planes).hasSize(3);
    assertThat(planes.get(0).get("code").asText()).isEqualTo("esencial");
    assertThat(planes.get(1).get("code").asText()).isEqualTo("profesional");
    assertThat(planes.get(2).get("code").asText()).isEqualTo("clinica");

    JsonNode esencial = planes.get(0);
    assertThat(esencial.get("name").asText()).isEqualTo("Esencial");
    assertThat(esencial.get("monthlyPriceCop").asInt()).isEqualTo(99900);
    assertThat(esencial.get("features")).isEmpty();
    assertThat(esencial.get("limits").get("max_patients").asInt()).isEqualTo(150);

    JsonNode clinica = planes.get(2);
    assertThat(clinica.get("features").toString()).contains("ai_assistant");
    assertThat(clinica.get("limits").get("whatsapp_conversations_month").asInt()).isEqualTo(1000);
    assertThat(clinica.get("limits").get("max_users")).isNull();
  }

  @Test
  void especialistaExternoRecibeForbidden() throws Exception {
    mockMvc.perform(
            get("/api/v1/billing/plans")
                .header("Authorization", "Bearer " + tokenEspecialista))
        .andExpect(status().isForbidden());
  }
}
