package com.julio.odentix.odentix_backend.saas;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.julio.odentix.odentix_backend.saas.entity.SaasPayment;
import com.julio.odentix.odentix_backend.saas.entity.SaasPaymentStatus;
import com.julio.odentix.odentix_backend.saas.repository.SaasPaymentRepository;
import com.julio.odentix.odentix_backend.saas.service.SaasRenewalJob;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
 * Pruebas de facturación SaaS con Bold (FASE11-04) contra PostgreSQL real y un
 * Bold falso local (cero red real, cero credenciales).
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Checkout genera el link y la suscripción; repetirlo reutiliza el link.</li>
 *   <li>Webhook con firma HMAC válida: aprobada → activa y extiende; duplicada →
 *       idempotente; firma mala → 400; rechazada → en mora.</li>
 *   <li>Job: mora más allá de la gracia → cancelada; periodo por vencer sin
 *       link pendiente → renovación creada.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SaasBillingIntegrationTest extends AbstractIntegrationTest {

  private static final String SECRETO_TEST = "secreto-test";

  private static HttpServer boldFalso;
  private static int puertoFalso;
  private static final AtomicReference<String> cuerpoLink = new AtomicReference<>();

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

  @Autowired
  private TenantSubscriptionRepository subscriptionRepository;

  @Autowired
  private SaasPaymentRepository paymentRepository;

  @Autowired
  private SaasRenewalJob renewalJob;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private String tokenA;

  @BeforeAll
  static void iniciarBoldFalso() throws IOException {
    boldFalso = HttpServer.create(new InetSocketAddress(0), 0);
    boldFalso.createContext("/online/link/v1", intercambio -> {
      cuerpoLink.set(new String(
          intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String json = "{\"payload\":{\"payment_link\":\"LNK_TEST\","
          + "\"url\":\"https://checkout.bold.co/LNK_TEST\"},\"errors\":[]}";
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
    registry.add("odentix.saas.bold.webhook-secret", () -> SECRETO_TEST);
  }

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica SaaS Alfa", "9M0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "propietario@saas-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenA = jwtService.generateToken(userA);
  }

  // ---------------------------------------------------------------------------
  // Helpers (HMAC según developers.bold.co: hex(HMAC-SHA256(Base64(rawBody))))
  // ---------------------------------------------------------------------------

  private static String firmar(String rawBody) throws Exception {
    String base64 = Base64.getEncoder()
        .encodeToString(rawBody.getBytes(StandardCharsets.UTF_8));
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    StringBuilder hex = new StringBuilder();
    for (byte b : mac.doFinal(base64.getBytes(StandardCharsets.UTF_8))) {
      hex.append(String.format("%02x", b));
    }
    return hex.toString();
  }

  private JsonNode checkout(String planCode) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/billing/checkout")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("planCode", planCode))))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private int webhook(String rawBody, String firma) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/billing/webhooks/bold")
            .header("x-bold-signature", firma != null ? firma : "")
            .contentType(MediaType.APPLICATION_JSON)
            .content(rawBody))
        .andReturn();
    return result.getResponse().getStatus();
  }

  private String evento(String id, String type, String reference) {
    return "{\"id\":\"" + id + "\",\"type\":\"" + type + "\",\"data\":{\"metadata\":{"
        + "\"reference\":" + (reference != null ? "\"" + reference + "\"" : "null") + "}}}";
  }

  private List<SaasPayment> pagosDe() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return paymentRepository.findAll();
    } finally {
      TenantContext.clear();
    }
  }

  private TenantSubscription suscripcionDe() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      return subscriptionRepository.findByTenantId(tenantA.getId()).get(0);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE11-04)
  // ---------------------------------------------------------------------------

  @Test
  void checkoutCreaLinkYReutilizaPendiente() throws Exception {
    JsonNode primero = checkout("profesional");

    assertThat(primero.get("planCode").asText()).isEqualTo("profesional");
    assertThat(primero.get("paymentUrl").asText()).contains("checkout.bold.co");
    assertThat(primero.get("status").asText()).isEqualTo("trialing");
    // Lo enviado a Bold: monto cerrado en COP con referencia única.
    assertThat(cuerpoLink.get()).contains("\"amount_type\":\"CLOSE\"");
    assertThat(cuerpoLink.get()).contains("169900");

    // Repetir el checkout reutiliza el link pendiente (no duplica cobros).
    JsonNode segundo = checkout("profesional");
    assertThat(segundo.get("paymentUrl").asText())
        .isEqualTo(primero.get("paymentUrl").asText());
    assertThat(pagosDe()).hasSize(1);
  }

  @Test
  void webhookAprobadoActivaYExtiendeIdempotente() throws Exception {
    checkout("profesional");
    String referencia = pagosDe().get(0).getBoldReference();

    String cuerpo = evento("notif-1", "SALE_APPROVED", referencia);
    assertThat(webhook(cuerpo, firmar(cuerpo))).isEqualTo(200);

    assertThat(pagosDe().get(0).getStatus()).isEqualTo(SaasPaymentStatus.pagada);
    TenantSubscription sub = suscripcionDe();
    assertThat(sub.getStatus()).isEqualTo(SubscriptionStatus.active);
    assertThat(sub.getCurrentPeriodEnd()).isAfter(Instant.now().plus(20, ChronoUnit.DAYS));

    // Reintento de Bold con el mismo id: 200 pero sin cambios.
    assertThat(webhook(cuerpo, firmar(cuerpo))).isEqualTo(200);
    assertThat(pagosDe()).hasSize(1);
  }

  @Test
  void webhookFirmaMalaDevuelve400() throws Exception {
    String cuerpo = evento("notif-x", "SALE_APPROVED", "cualquiera");
    assertThat(webhook(cuerpo, "firma-invalida")).isEqualTo(400);
    assertThat(pagosDe()).isEmpty();
  }

  @Test
  void webhookRechazadoDejaEnMora() throws Exception {
    checkout("esencial");
    String referencia = pagosDe().get(0).getBoldReference();

    String cuerpo = evento("notif-2", "SALE_REJECTED", referencia);
    assertThat(webhook(cuerpo, firmar(cuerpo))).isEqualTo(200);

    assertThat(pagosDe().get(0).getStatus()).isEqualTo(SaasPaymentStatus.rechazada);
    assertThat(suscripcionDe().getStatus()).isEqualTo(SubscriptionStatus.past_due);
  }

  @Test
  void jobCancelaMoraVencidaYRenuevaPorVencer() {
    Plan profesional = planRepository.findByCode("profesional").orElseThrow();
    Tenant tenantB = tenantRepository.save(new Tenant("Clínica SaaS Beta", "9M0133444-2"));

    // En mora con periodo vencido hace 10 días (más que la gracia de 7).
    // Tenant propio: el índice parcial impide dos suscripciones vivas.
    TenantContext.setTenantId(tenantA.getId());
    TenantSubscription morosa;
    try {
      morosa = new TenantSubscription(tenantA.getId(), profesional,
          Instant.now().minus(10, ChronoUnit.DAYS));
      morosa.setStatus(SubscriptionStatus.past_due);
      morosa = subscriptionRepository.saveAndFlush(morosa);
    } finally {
      TenantContext.clear();
    }

    // Activa con periodo que vence en 1 día y sin link pendiente.
    TenantSubscription porVencer;
    TenantContext.setTenantId(tenantB.getId());
    try {
      porVencer = new TenantSubscription(tenantB.getId(), profesional,
          Instant.now().plus(1, ChronoUnit.DAYS));
      porVencer.setStatus(SubscriptionStatus.active);
      porVencer = subscriptionRepository.saveAndFlush(porVencer);
    } finally {
      TenantContext.clear();
    }

    TenantContext.clear();
    int[] resultado = renewalJob.execute();

    assertThat(resultado[1]).isEqualTo(1);
    assertThat(resultado[0]).isEqualTo(1);
    assertThat(subscriptionRepository.findById(morosa.getId()).orElseThrow().getStatus())
        .isEqualTo(SubscriptionStatus.cancelled);
    assertThat(paymentRepository.findBySubscriptionIdAndStatus(
        porVencer.getId(), SaasPaymentStatus.pendiente)).hasSize(1);
  }
}
