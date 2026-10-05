package com.julio.odentix.odentix_backend.subscription;

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
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para el resumen del plan
 * ({@code GET /api/v1/billing/plan}) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre: features y límites del plan Esencial frente al Clínica, lectura
 * por roles no propietarios (la navegación la necesita), fail-open sin
 * suscripción y 403 para el especialista externo.
 */
@AutoConfigureMockMvc
class PlanSummaryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private PlanRepository planRepository;

  @Autowired
  private TenantSubscriptionRepository subscriptionRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant esencial;
  private Tenant clinica;
  private Tenant sinSuscripcion;
  private String tokenRecepcionEsencial;
  private String tokenEspecialistaEsencial;
  private String tokenPropietarioClinica;
  private String tokenSinSuscripcion;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    esencial = tenantRepository.save(new Tenant("Clínica Plan Esencial", "9M0111222-1"));
    suscribir(esencial.getId(), "esencial");
    User recepcion = userService.createUser(
        esencial.getId(), "recepcion@plan-esencial.com", "ClaveSegura123!", "Recepción",
        UserRole.recepcion);
    tokenRecepcionEsencial = jwtService.generateToken(recepcion);
    User especialista = userService.createUser(
        esencial.getId(), "externo@plan-esencial.com", "ClaveSegura123!", "Ed Externo",
        UserRole.especialista_externo);
    tokenEspecialistaEsencial = jwtService.generateToken(especialista);

    clinica = tenantRepository.save(new Tenant("Clínica Plan Clínica", "9M0133444-2"));
    suscribir(clinica.getId(), "clinica");
    User propietario = userService.createUser(
        clinica.getId(), "propietario@plan-clinica.com", "ClaveSegura123!", "Propietaria",
        UserRole.propietario);
    tokenPropietarioClinica = jwtService.generateToken(propietario);

    sinSuscripcion = tenantRepository.save(new Tenant("Clínica Sin Plan", "9M0155666-3"));
    User recepcionLibre = userService.createUser(
        sinSuscripcion.getId(), "recepcion@sin-plan.com", "ClaveSegura123!", "Recepción Libre",
        UserRole.recepcion);
    tokenSinSuscripcion = jwtService.generateToken(recepcionLibre);
  }

  private void suscribir(UUID tenantId, String planCode) {
    Plan plan = planRepository.findByCode(planCode).orElseThrow();
    TenantContext.setTenantId(tenantId);
    try {
      subscriptionRepository.saveAndFlush(
          new TenantSubscription(tenantId, plan, Instant.now().plusSeconds(2_592_000)));
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode resumen(String token) throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/billing/plan")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void esencialNoExponeFeaturesPeroSiLimits() throws Exception {
    JsonNode resumen = resumen(tokenRecepcionEsencial);

    assertThat(resumen.get("planCode").asText()).isEqualTo("esencial");
    assertThat(resumen.get("features")).isEmpty();
    assertThat(resumen.get("limits").get("max_patients").asInt()).isEqualTo(150);
    assertThat(resumen.get("limits").get("max_users").asInt()).isEqualTo(2);
  }

  @Test
  void clinicaExponeFeaturesHabilitados() throws Exception {
    JsonNode resumen = resumen(tokenPropietarioClinica);

    assertThat(resumen.get("planCode").asText()).isEqualTo("clinica");
    assertThat(resumen.get("features").toString()).contains("opportunities_engine");
    assertThat(resumen.get("features").toString()).contains("ai_assistant");
    assertThat(resumen.get("features").toString()).contains("crm_leads");
    assertThat(resumen.get("features").toString()).contains("cartera");
    assertThat(resumen.get("features").toString()).contains("specialists");
    assertThat(resumen.get("features").toString()).contains("inventory");
  }

  @Test
  void sinSuscripcionDevuelveCodigoNulo() throws Exception {
    JsonNode resumen = resumen(tokenSinSuscripcion);

    assertThat(resumen.get("planCode").isNull()).isTrue();
    assertThat(resumen.get("features")).isEmpty();
  }

  @Test
  void especialistaExternoRecibeForbidden() throws Exception {
    mockMvc.perform(
            get("/api/v1/billing/plan")
                .header("Authorization", "Bearer " + tokenEspecialistaEsencial))
        .andExpect(status().isForbidden());
  }
}
