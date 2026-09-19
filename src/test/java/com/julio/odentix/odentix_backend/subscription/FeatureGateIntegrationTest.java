package com.julio.odentix.odentix_backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistRepository;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
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
 * Pruebas del feature-gating por plan (FASE11-02) contra PostgreSQL real vía
 * Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Un tenant Esencial recibe 403 con mensaje de upgrade al usar endpoints
 *       de planes superiores (leads, cartera, especialistas, inventario,
 *       oportunidades, IA).</li>
 *   <li>Profesional accede a lo suyo pero no a IA ni alertas (solo Clínica).</li>
 *   <li>Clínica accede a todo (la IA responde 502 sin key, que igual prueba
 *       que pasó el gate).</li>
 *   <li>Tenant sin suscripción viva entra por fail-open pre-billing.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class FeatureGateIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PlanRepository planRepository;

  @Autowired
  private TenantSubscriptionRepository subscriptionRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private SpecialistRepository specialistRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant esencial;
  private Tenant profesional;
  private Tenant clinica;
  private Tenant libre;
  private String tokenEsencial;
  private String tokenProfesional;
  private String tokenClinica;
  private String tokenLibre;
  private Specialist specialistEsencial;
  private Specialist specialistProfesional;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    esencial = crearTenantConPlan("Clínica Gate Esencial", "9K0111222-1", "esencial",
        "propietario@gate-esencial.com");
    tokenEsencial = tokenPara(esencial, "propietario@gate-esencial.com");
    profesional = crearTenantConPlan("Clínica Gate Profesional", "9K0133444-2", "profesional",
        "propietario@gate-profesional.com");
    tokenProfesional = tokenPara(profesional, "propietario@gate-profesional.com");
    clinica = crearTenantConPlan("Clínica Gate Clínica", "9K0155666-3", "clinica",
        "propietario@gate-clinica.com");
    tokenClinica = tokenPara(clinica, "propietario@gate-clinica.com");

    libre = tenantRepository.save(new Tenant("Clínica Gate Libre", "9K0177888-4"));
    User userLibre = userService.createUser(
        libre.getId(), "propietario@gate-libre.com", "ClaveSegura123!", "Propietario Libre",
        UserRole.propietario);
    tokenLibre = jwtService.generateToken(userLibre);

    specialistEsencial = crearSpecialist(esencial.getId());
    specialistProfesional = crearSpecialist(profesional.getId());
  }

  private Tenant crearTenantConPlan(String nombre, String nit, String planCode, String email) {
    Tenant tenant = tenantRepository.save(new Tenant(nombre, nit));
    userService.createUser(tenant.getId(), email, "ClaveSegura123!", "Propietario",
        UserRole.propietario);
    Plan plan = planRepository.findByCode(planCode).orElseThrow();
    TenantContext.setTenantId(tenant.getId());
    try {
      subscriptionRepository.saveAndFlush(
          new TenantSubscription(tenant.getId(), plan, Instant.now().plusSeconds(2_592_000)));
    } finally {
      TenantContext.clear();
    }
    return tenant;
  }

  private String tokenPara(Tenant tenant, String email) {
    User user = userRepository.findByTenantIdAndEmail(tenant.getId(), email).orElseThrow();
    return jwtService.generateToken(user);
  }

  private Specialist crearSpecialist(UUID tenantId) {
    TenantContext.setTenantId(tenantId);
    try {
      Professional externo = new Professional(tenantId, "Especialista Gate");
      externo.setExternal(true);
      externo = professionalRepository.saveAndFlush(externo);
      return specialistRepository.saveAndFlush(
          new Specialist(tenantId, externo, new BigDecimal("40.00")));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers HTTP
  // ---------------------------------------------------------------------------

  private int postEstado(String token, String url, Object body) throws Exception {
    MvcResult result = mockMvc.perform(post(url)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andReturn();
    return result.getResponse().getStatus();
  }

  private int getEstado(String token, String url) throws Exception {
    MvcResult result = mockMvc.perform(get(url)
            .header("Authorization", "Bearer " + token))
        .andReturn();
    return result.getResponse().getStatus();
  }

  private String cuerpo403(String token, String url, Object body) throws Exception {
    MvcResult result = mockMvc.perform(post(url)
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden())
        .andReturn();
    return result.getResponse().getContentAsString();
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE11-02)
  // ---------------------------------------------------------------------------

  @Test
  void esencialRecibe403ConMensajeDeUpgrade() throws Exception {
    Map<String, Object> lead = Map.of("fullName", "Lead Esencial");
    assertThat(cuerpo403(tokenEsencial, "/api/v1/leads", lead)).contains("esencial");

    assertThat(getEstado(tokenEsencial, "/api/v1/portfolio/summary")).isEqualTo(403);
    assertThat(getEstado(tokenEsencial, "/api/v1/opportunities")).isEqualTo(403);
    assertThat(getEstado(tokenEsencial, "/api/v1/inventory/critical")).isEqualTo(403);

    Map<String, Object> item = Map.of("name", "Insumo", "unit", "u", "minThreshold", 1);
    assertThat(postEstado(tokenEsencial, "/api/v1/inventory/items", item)).isEqualTo(403);

    Map<String, Object> periodo = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    assertThat(postEstado(tokenEsencial,
        "/api/v1/specialists/" + specialistEsencial.getId() + "/settlements", periodo))
        .isEqualTo(403);

    Map<String, Object> pregunta = Map.of("question", "¿cómo voy?");
    assertThat(postEstado(tokenEsencial, "/api/v1/assistant/ask", pregunta)).isEqualTo(403);
  }

  @Test
  void profesionalAccedeALoSuyoPeroNoAIANiAlertas() throws Exception {
    Map<String, Object> lead = Map.of("fullName", "Lead Pro");
    assertThat(postEstado(tokenProfesional, "/api/v1/leads", lead)).isEqualTo(201);
    assertThat(getEstado(tokenProfesional, "/api/v1/portfolio/summary")).isEqualTo(200);
    assertThat(getEstado(tokenProfesional, "/api/v1/opportunities")).isEqualTo(200);

    Map<String, Object> periodo = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    assertThat(postEstado(tokenProfesional,
        "/api/v1/specialists/" + specialistProfesional.getId() + "/settlements", periodo))
        .isEqualTo(201);

    Map<String, Object> pregunta = Map.of("question", "¿cómo voy?");
    assertThat(postEstado(tokenProfesional, "/api/v1/assistant/ask", pregunta)).isEqualTo(403);
    assertThat(getEstado(tokenProfesional, "/api/v1/inventory/critical")).isEqualTo(403);
  }

  @Test
  void clinicaAccedeATodo() throws Exception {
    assertThat(getEstado(tokenClinica, "/api/v1/inventory/critical")).isEqualTo(200);
    assertThat(getEstado(tokenClinica, "/api/v1/opportunities")).isEqualTo(200);
    // Sin GROQ_API_KEY el fallback de FASE10-03 responde 200 con flag, lo que
    // igual prueba que pasó el gate (no 403).
    MvcResult ask = mockMvc.perform(post("/api/v1/assistant/ask")
            .header("Authorization", "Bearer " + tokenClinica)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("question", "¿cómo voy?"))))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(objectMapper.readTree(ask.getResponse().getContentAsString())
        .get("fallback").asBoolean()).isTrue();
  }

  @Test
  void tenantSinSuscripcionEntraPorFailOpen() throws Exception {
    Map<String, Object> lead = Map.of("fullName", "Lead Libre");
    assertThat(postEstado(tokenLibre, "/api/v1/leads", lead)).isEqualTo(201);
    assertThat(getEstado(tokenLibre, "/api/v1/opportunities")).isEqualTo(200);
  }
}
