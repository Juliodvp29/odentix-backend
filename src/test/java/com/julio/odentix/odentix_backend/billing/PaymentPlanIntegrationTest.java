package com.julio.odentix.odentix_backend.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanItemRepository;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Pruebas de integración HTTP para planes de pago en cuotas (FASE6-02)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Crear un plan de pago → 201 con las N cuotas en estado {@code pendiente}.</li>
 *   <li>Pagar una cuota → cuota pasa a {@code pagada} con {@code paidAt} registrado.</li>
 *   <li>Pagar una cuota ya pagada → 409 Conflict.</li>
 *   <li>Crear plan para tratamiento de otro tenant → 404 (aislamiento cross-tenant).</li>
 *   <li>Pagar cuota de otro tenant → 404 (aislamiento cross-tenant).</li>
 *   <li>Crear segundo plan para el mismo tratamiento → 409 Conflict.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class PaymentPlanIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private TreatmentPlanItemRepository treatmentPlanItemRepository;

  @Autowired
  private InstallmentRepository installmentRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private TreatmentPlan planTratamientoA;
  private TreatmentPlan planTratamientoB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Cartera HTTP Alfa", "930111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@cartera-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Cartera HTTP Beta", "931333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@cartera-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient pacienteA = patientRepository.save(new Patient(tenantA.getId(), "Marta", "López"));
      planTratamientoA = treatmentPlanRepository.save(new TreatmentPlan(tenantA.getId(), pacienteA));
      treatmentPlanItemRepository.save(new TreatmentPlanItem(
          tenantA.getId(), planTratamientoA, new BigDecimal("900000.00")));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient pacienteB = patientRepository.save(new Patient(tenantB.getId(), "Jorge", "Ríos"));
      planTratamientoB = treatmentPlanRepository.save(new TreatmentPlan(tenantB.getId(), pacienteB));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private String crearPlanPago(String token, String treatmentPlanId,
      String total, int cuotas) throws Exception {
    Map<String, Object> body = Map.of(
        "totalAmountCop", new BigDecimal(total),
        "installmentsCount", cuotas);
    MvcResult result = mockMvc.perform(
            post("/api/v1/treatment-plans/{id}/payment-plan", treatmentPlanId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return json.get("id").asText();
  }

  private String idPrimeraCuota(String paymentPlanId) {
    // Obtener el ID de la primera cuota de un plan de pago vía repositorio.
    return installmentRepository
        .findByPaymentPlanIdOrderByInstallmentNumberAsc(
            java.util.UUID.fromString(paymentPlanId))
        .get(0).getId().toString();
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE6-02)
  // ---------------------------------------------------------------------------

  @Test
  void crearPlanPagoDevuelveNcuotasEnPendiente() throws Exception {
    Map<String, Object> body = Map.of(
        "totalAmountCop", new BigDecimal("900000.00"),
        "installmentsCount", 3);

    mockMvc.perform(post("/api/v1/treatment-plans/{id}/payment-plan",
                planTratamientoA.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.treatmentPlanId").value(planTratamientoA.getId().toString()))
        .andExpect(jsonPath("$.totalAmountCop").value(900000.00))
        .andExpect(jsonPath("$.installmentsCount").value(3))
        .andExpect(jsonPath("$.installments.length()").value(3))
        // Toda cuota nace en pendiente (DoD).
        .andExpect(jsonPath("$.installments[0].status").value("pendiente"))
        .andExpect(jsonPath("$.installments[1].status").value("pendiente"))
        .andExpect(jsonPath("$.installments[2].status").value("pendiente"))
        // La suma de cuotas cierra: 300000 × 2 + 300000 = 900000.
        .andExpect(jsonPath("$.installments[0].amountCop").value(300000.00))
        .andExpect(jsonPath("$.installments[1].amountCop").value(300000.00))
        .andExpect(jsonPath("$.installments[2].amountCop").value(300000.00));
  }

  @Test
  void pagarCuotaLaMarcaComoPagadaYRegistraTimestamp() throws Exception {
    String planId = crearPlanPago(tokenA, planTratamientoA.getId().toString(), "600000.00", 2);
    String cuotaId = idPrimeraCuota(planId);

    mockMvc.perform(post("/api/v1/installments/{id}/pay", cuotaId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(cuotaId))
        // DoD: cuota pasa a pagada y queda reflejada en cartera.
        .andExpect(jsonPath("$.status").value("pagada"))
        .andExpect(jsonPath("$.paidAt").exists())
        .andExpect(jsonPath("$.paidAt", Matchers.not(Matchers.emptyString())));
  }

  @Test
  void pagarCuotaYaPagedaDevuelve409() throws Exception {
    String planId = crearPlanPago(tokenA, planTratamientoA.getId().toString(), "600000.00", 2);
    String cuotaId = idPrimeraCuota(planId);

    // Primera vez: OK.
    mockMvc.perform(post("/api/v1/installments/{id}/pay", cuotaId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk());

    // Segunda vez: 409 Conflict.
    mockMvc.perform(post("/api/v1/installments/{id}/pay", cuotaId)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", Matchers.containsString("ya fue pagada")));
  }

  @Test
  void segundoPlanParaMismoTratamientoDevuelve409() throws Exception {
    // Primer plan: OK.
    crearPlanPago(tokenA, planTratamientoA.getId().toString(), "900000.00", 3);

    // Segundo plan para el mismo tratamiento: 409 Conflict.
    Map<String, Object> body = Map.of(
        "totalAmountCop", new BigDecimal("500000.00"),
        "installmentsCount", 2);
    mockMvc.perform(post("/api/v1/treatment-plans/{id}/payment-plan",
                planTratamientoA.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", Matchers.containsString("ya tiene un plan de pago")));
  }

  // ---------------------------------------------------------------------------
  // Tests de aislamiento cross-tenant (regla §5 de AGENTS.md — obligatorio)
  // ---------------------------------------------------------------------------

  @Test
  void crearPlanParaTratamientoDeOtroTenantDevuelve404() throws Exception {
    // tokenA intenta crear un plan sobre el tratamiento de tenantB.
    Map<String, Object> body = Map.of(
        "totalAmountCop", new BigDecimal("500000.00"),
        "installmentsCount", 2);
    mockMvc.perform(post("/api/v1/treatment-plans/{id}/payment-plan",
                planTratamientoB.getId())
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  void pagarCuotaDeOtroTenantDevuelve404() throws Exception {
    // Crear plan con tokenB (tenantB).
    String planIdB = crearPlanPago(tokenB, planTratamientoB.getId().toString(), "600000.00", 2);
    String cuotaIdB = idPrimeraCuota(planIdB);

    // tokenA (tenantA) intenta pagar la cuota de tenantB → 404.
    mockMvc.perform(post("/api/v1/installments/{id}/pay", cuotaIdB)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }
}
