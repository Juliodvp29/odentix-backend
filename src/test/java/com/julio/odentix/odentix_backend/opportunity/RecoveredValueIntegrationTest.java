package com.julio.odentix.odentix_backend.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.opportunity.service.LeadUnrespondedJob;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de la métrica de valor recuperado (FASE9-04) contra PostgreSQL real
 * vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Resuelta con acción ejecutada previa dentro del periodo → suma por categoría.</li>
 *   <li>Resuelta sin acción, con acción posterior o fuera de periodo → excluida.</li>
 *   <li>Aislamiento cross-tenant y validación del periodo.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class RecoveredValueIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private LeadRepository leadRepository;

  @Autowired
  private LeadUnrespondedJob leadUnrespondedJob;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Recupera Alfa", "9H0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@recupera-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Recupera Beta", "9H0133444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@recupera-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void crearLeadAntiguo(UUID tenantId, String nombre, String valor) {
    TenantContext.setTenantId(tenantId);
    try {
      Lead lead = new Lead(tenantId, nombre, "573006667788", nombre + "@x.com", "web");
      lead.setEstimatedValueCop(new BigDecimal(valor));
      lead.setCreatedAt(Instant.now().minus(30, ChronoUnit.HOURS));
      leadRepository.saveAndFlush(lead);
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode bandeja(String token) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/opportunities")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private void ejecutarTarea(String token, String oppId) throws Exception {
    JsonNode bandeja = bandeja(token);
    String tareaId = null;
    for (JsonNode opp : bandeja) {
      if (opp.get("id").asText().equals(oppId)) {
        for (JsonNode accion : opp.get("actions")) {
          if (accion.get("actionType").asText().equals("crear_tarea")) {
            tareaId = accion.get("id").asText();
          }
        }
      }
    }
    assertThat(tareaId).isNotNull();
    mockMvc.perform(
            post("/api/v1/opportunities/{id}/actions/{actionId}/execute", oppId, tareaId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  private void resolver(String token, String oppId) throws Exception {
    mockMvc.perform(patch("/api/v1/opportunities/{id}/status", oppId)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("status", "resuelta"))))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.status").value("resuelta"));
  }

  private JsonNode valorRecuperado(String token, Instant from, Instant to) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/opportunities/recovered-value")
            .header("Authorization", "Bearer " + token)
            .param("from", from.toString())
            .param("to", to.toString()))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private String oppIdDe(JsonNode bandeja, int indice) {
    return bandeja.get(indice).get("id").asText();
  }

  private String oppIdPorValor(JsonNode bandeja, String valor) {
    for (JsonNode opp : bandeja) {
      if (new BigDecimal(opp.get("estimatedValueCop").asText()).compareTo(new BigDecimal(valor))
          == 0) {
        return opp.get("id").asText();
      }
    }
    throw new AssertionError("Sin oportunidad con valor " + valor);
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE9-04)
  // ---------------------------------------------------------------------------

  @Test
  void resueltaConAccionPreviaSumaPorCategoria() throws Exception {
    crearLeadAntiguo(tenantA.getId(), "Recuperado Uno", "800000.00");
    crearLeadAntiguo(tenantA.getId(), "Recuperado Dos", "400000.00");
    crearLeadAntiguo(tenantA.getId(), "Sin Accion", "900000.00");
    crearLeadAntiguo(tenantA.getId(), "Accion Tardia", "700000.00");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    JsonNode bandeja = bandeja(tokenA);
    assertThat(bandeja).hasSize(4);

    // Dos con acción ejecutada antes de resolver → cuentan (800k + 400k).
    String opp1 = oppIdPorValor(bandeja, "800000.00");
    String opp2 = oppIdPorValor(bandeja, "400000.00");
    ejecutarTarea(tokenA, opp1);
    ejecutarTarea(tokenA, opp2);
    resolver(tokenA, opp1);
    resolver(tokenA, opp2);

    // Resuelta sin acción → orgánica, no cuenta.
    resolver(tokenA, oppIdPorValor(bandeja, "900000.00"));

    // Acción ejecutada DESPUÉS de resolver → rompe el orden causal, no cuenta.
    String tardia = oppIdPorValor(bandeja, "700000.00");
    resolver(tokenA, tardia);
    ejecutarTarea(tokenA, tardia);

    Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant to = Instant.now().plus(1, ChronoUnit.DAYS);
    JsonNode metricas = valorRecuperado(tokenA, from, to);

    assertThat(metricas).hasSize(1);
    assertThat(metricas.get(0).get("type").asText()).isEqualTo("lead_sin_respuesta");
    assertThat(new BigDecimal(metricas.get(0).get("totalAmountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("1200000.00"));
    assertThat(metricas.get(0).get("count").asLong()).isEqualTo(2L);

    // Periodo futuro sin resoluciones → vacío.
    JsonNode vacio = valorRecuperado(tokenA,
        Instant.now().plus(30, ChronoUnit.DAYS), Instant.now().plus(31, ChronoUnit.DAYS));
    assertThat(vacio).hasSize(0);
  }

  @Test
  void periodoInvalidoDevuelve400YSinToken401() throws Exception {
    Instant ahora = Instant.now();
    mockMvc.perform(get("/api/v1/opportunities/recovered-value")
            .header("Authorization", "Bearer " + tokenA)
            .param("from", ahora.toString())
            .param("to", ahora.minus(1, ChronoUnit.HOURS).toString()))
        .andExpect(status().isBadRequest());

    mockMvc.perform(get("/api/v1/opportunities/recovered-value")
            .param("from", ahora.minus(1, ChronoUnit.HOURS).toString())
            .param("to", ahora.toString()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void valorDeOtroTenantNoSeFiltra() throws Exception {
    crearLeadAntiguo(tenantB.getId(), "Recuperado Beta", "500000.00");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    JsonNode bandejaB = bandeja(tokenB);
    String oppB = oppIdDe(bandejaB, 0);
    ejecutarTarea(tokenB, oppB);
    resolver(tokenB, oppB);

    Instant from = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant to = Instant.now().plus(1, ChronoUnit.DAYS);

    // Visible en B…
    JsonNode metricasB = valorRecuperado(tokenB, from, to);
    assertThat(metricasB).hasSize(1);
    assertThat(new BigDecimal(metricasB.get(0).get("totalAmountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("500000.00"));

    // …e invisible desde A.
    assertThat(valorRecuperado(tokenA, from, to)).hasSize(0);
  }
}
