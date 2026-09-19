package com.julio.odentix.odentix_backend.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas del ciclo de facturación anual (FASE11-05).
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>El tenant elige mensual o anual al suscribirse y el monto refleja el
 *       precio del ciclo (el descuento vive en los seeds y queda blindado).</li>
 *   <li>`GET /subscription` expone ciclo, periodo y ambos precios.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class BillingCycleIntegrationTest extends AbstractIntegrationTest {

  private static HttpServer boldFalso;
  private static int puertoFalso;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PlanRepository planRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private String tokenA;

  @BeforeAll
  static void iniciarBoldFalso() throws IOException {
    boldFalso = HttpServer.create(new InetSocketAddress(0), 0);
    boldFalso.createContext("/online/link/v1", intercambio -> {
      String json = "{\"payload\":{\"payment_link\":\"LNK_CICLO\","
          + "\"url\":\"https://checkout.bold.co/LNK_CICLO\"},\"errors\":[]}";
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(201, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    boldFalso.start();
    puertoFalso = boldFalso.getAddress().getPort();
  }

  @AfterAll
  static void detenerBoldFalso() {
    if (boldFalso != null) {
      boldFalso.stop(0);
    }
  }

  @DynamicPropertySource
  static void boldFalso(DynamicPropertyRegistry registry) {
    registry.add("odentix.saas.bold.api-base-url", () -> "http://localhost:" + puertoFalso);
    registry.add("odentix.saas.bold.api-key", () -> "key-falsa");
    registry.add("odentix.saas.bold.webhook-secret", () -> "");
  }

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Ciclo Alfa", "9N0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "propietario@ciclo-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenA = jwtService.generateToken(userA);
  }

  private JsonNode checkout(String planCode, String ciclo) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/billing/checkout")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("planCode", planCode, "billingCycle", ciclo))))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode suscripcion() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/billing/subscription")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void descuentoAnualBlindadoEnSeeds() {
    // El "descuento ya definido" del ticket: anual < 12×mensual en los 3 planes.
    // Si un seed futuro lo rompe, este test lo atrapa (precios = decisión tomada).
    for (String code : new String[]{"esencial", "profesional", "clinica"}) {
      Plan plan = planRepository.findByCode(code).orElseThrow();
      assertThat(plan.getAnnualPriceCop())
          .isLessThan(plan.getMonthlyPriceCop().multiply(new BigDecimal("12")));
    }
  }

  @Test
  void elegirAnualCobraPrecioAnualYCambiarCicloFunciona() throws Exception {
    // Mensual primero.
    JsonNode mensual = checkout("profesional", "monthly");
    assertThat(mensual.get("billingCycle").asText()).isEqualTo("monthly");
    assertThat(new BigDecimal(mensual.get("amountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("169900.00"));

    // Cambio a anual: el monto refleja el descuento.
    JsonNode anual = checkout("profesional", "annual");
    assertThat(anual.get("billingCycle").asText()).isEqualTo("annual");
    assertThat(new BigDecimal(anual.get("amountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("1699000.00"));

    // La suscripción expone ciclo, periodo y ambos precios.
    JsonNode sub = suscripcion();
    assertThat(sub.get("planCode").asText()).isEqualTo("profesional");
    assertThat(sub.get("billingCycle").asText()).isEqualTo("annual");
    assertThat(sub.get("status").asText()).isEqualTo("trialing");
    assertThat(sub.get("currentPeriodEnd").asText()).isNotBlank();
    assertThat(new BigDecimal(sub.get("annualPriceCop").asText()))
        .isEqualByComparingTo(new BigDecimal("1699000.00"));
  }

  @Test
  void sinSuscripcionDevuelve404() throws Exception {
    mockMvc.perform(get("/api/v1/billing/subscription")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }
}
