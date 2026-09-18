package com.julio.odentix.odentix_backend.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración HTTP para el dashboard de cartera (FASE6-04)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>{@code GET /api/v1/portfolio/summary} devuelve totales coherentes con los
 *       datos de {@code Installment} en ese momento (cartera total, vencida, por vencer y al día).</li>
 *   <li>Clínica sin cuotas devuelve ceros limpios (sin nulos ni errores 500).</li>
 *   <li>Aislamiento cross-tenant estricto (§5 de AGENTS.md): las cuotas de un tenant
 *       no contaminan los totales de otro tenant.</li>
 *   <li>Acceso no autenticado devuelve 401 Unauthorized.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class PortfolioIntegrationTest extends AbstractIntegrationTest {

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
  private PaymentPlanRepository paymentPlanRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private TreatmentPlan planTratamientoA;
  private TreatmentPlan planTratamientoB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Cartera Dashboard Alfa", "950111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@dashboard-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Cartera Dashboard Beta", "951333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@dashboard-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient pacienteA = patientRepository.save(new Patient(tenantA.getId(), "Mario", "Duarte"));
      planTratamientoA = treatmentPlanRepository.save(new TreatmentPlan(tenantA.getId(), pacienteA));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient pacienteB = patientRepository.save(new Patient(tenantB.getId(), "Sara", "Castro"));
      planTratamientoB = treatmentPlanRepository.save(new TreatmentPlan(tenantB.getId(), pacienteB));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void crearCuota(UUID tenantId, TreatmentPlan treatmentPlan,
      int numeroCuota, BigDecimal monto, LocalDate fechaVencimiento,
      InstallmentStatus estadoInicial, Instant paidAt) {
    TenantContext.setTenantId(tenantId);
    try {
      PaymentPlan plan = new PaymentPlan();
      plan.setTenantId(tenantId);
      plan.setTreatmentPlanId(treatmentPlan.getId());
      plan.setTotalAmountCop(monto);
      plan.setInstallmentsCount(1);

      Installment cuota = new Installment();
      cuota.setTenantId(tenantId);
      cuota.setInstallmentNumber(numeroCuota);
      cuota.setAmountCop(monto);
      cuota.setDueDate(fechaVencimiento);
      cuota.setStatus(estadoInicial);
      cuota.setPaidAt(paidAt);
      plan.addInstallment(cuota);

      paymentPlanRepository.saveAndFlush(plan);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de aceptación DoD (FASE6-04)
  // ---------------------------------------------------------------------------

  @Test
  void getPortfolioSummaryDevuelveTotalesCoherentes() throws Exception {
    LocalDate hoy = LocalDate.now();
    LocalDate ayer = hoy.minusDays(1);
    LocalDate manana = hoy.plusMonths(1);

    // 1. Cuota pagada (al día): 300,000 COP
    crearCuota(tenantA.getId(), planTratamientoA, 1, new BigDecimal("300000.00"),
        ayer, InstallmentStatus.pagada, Instant.now());

    // 2. Cuota en estado vencida: 200,000 COP
    crearCuota(tenantA.getId(), planTratamientoA, 1, new BigDecimal("200000.00"),
        ayer, InstallmentStatus.vencida, null);

    // 3. Cuota en estado pendiente pero con fecha pasada (vencida en tiempo real): 100,000 COP
    crearCuota(tenantA.getId(), planTratamientoA, 1, new BigDecimal("100000.00"),
        ayer, InstallmentStatus.pendiente, null);

    // 4. Cuota por vencer (pendiente con fecha futura): 400,000 COP
    crearCuota(tenantA.getId(), planTratamientoA, 1, new BigDecimal("400000.00"),
        manana, InstallmentStatus.pendiente, null);

    // Totales esperados:
    // Total pactado = 300k + 200k + 100k + 400k = 1,000,000 COP
    // Vencida = 200k + 100k = 300,000 COP
    // Por vencer = 400,000 COP
    // Al día (pagada) = 300,000 COP
    // Saldo por cobrar (outstanding) = 300k + 400k = 700,000 COP
    // Conteo total = 4 (vencidas: 2, por vencer: 1, pagadas: 1)

    mockMvc.perform(get("/api/v1/portfolio/summary")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmountCop").value(1000000.00))
        .andExpect(jsonPath("$.overdueAmountCop").value(300000.00))
        .andExpect(jsonPath("$.upcomingAmountCop").value(400000.00))
        .andExpect(jsonPath("$.paidAmountCop").value(300000.00))
        .andExpect(jsonPath("$.outstandingAmountCop").value(700000.00))
        .andExpect(jsonPath("$.totalInstallmentsCount").value(4))
        .andExpect(jsonPath("$.overdueInstallmentsCount").value(2))
        .andExpect(jsonPath("$.upcomingInstallmentsCount").value(1))
        .andExpect(jsonPath("$.paidInstallmentsCount").value(1));
  }

  @Test
  void clinicaSinCuotasDevuelveCerosLimpios() throws Exception {
    // Tenant B no tiene ninguna cuota
    mockMvc.perform(get("/api/v1/portfolio/summary")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmountCop").value(0.00))
        .andExpect(jsonPath("$.overdueAmountCop").value(0.00))
        .andExpect(jsonPath("$.upcomingAmountCop").value(0.00))
        .andExpect(jsonPath("$.paidAmountCop").value(0.00))
        .andExpect(jsonPath("$.outstandingAmountCop").value(0.00))
        .andExpect(jsonPath("$.totalInstallmentsCount").value(0))
        .andExpect(jsonPath("$.overdueInstallmentsCount").value(0))
        .andExpect(jsonPath("$.upcomingInstallmentsCount").value(0))
        .andExpect(jsonPath("$.paidInstallmentsCount").value(0));
  }

  @Test
  void aislamientoCrossTenantEntreClinicas() throws Exception {
    // Tenant A tiene cuota de 500,000 COP
    crearCuota(tenantA.getId(), planTratamientoA, 1, new BigDecimal("500000.00"),
        LocalDate.now().plusMonths(1), InstallmentStatus.pendiente, null);

    // Tenant B tiene cuota de 150,000 COP
    crearCuota(tenantB.getId(), planTratamientoB, 1, new BigDecimal("150000.00"),
        LocalDate.now().plusMonths(2), InstallmentStatus.pendiente, null);

    // Tenant A solo ve 500,000 COP
    mockMvc.perform(get("/api/v1/portfolio/summary")
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmountCop").value(500000.00))
        .andExpect(jsonPath("$.upcomingAmountCop").value(500000.00))
        .andExpect(jsonPath("$.totalInstallmentsCount").value(1));

    // Tenant B solo ve 150,000 COP
    mockMvc.perform(get("/api/v1/portfolio/summary")
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAmountCop").value(150000.00))
        .andExpect(jsonPath("$.upcomingAmountCop").value(150000.00))
        .andExpect(jsonPath("$.totalInstallmentsCount").value(1));
  }

  @Test
  void sinAutenticacionDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/portfolio/summary"))
        .andExpect(status().isUnauthorized());
  }
}
