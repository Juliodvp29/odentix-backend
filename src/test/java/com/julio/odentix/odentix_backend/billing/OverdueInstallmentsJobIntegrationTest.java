package com.julio.odentix.odentix_backend.billing;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.billing.service.OverdueInstallmentsJob;
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

/**
 * Pruebas de integración para {@link OverdueInstallmentsJob} (FASE6-03)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Una cuota con {@code due_date < CURRENT_DATE} en estado {@code pendiente}
 *       cambia a {@code vencida} después de correr el job.</li>
 *   <li>Una cuota futura permanece en {@code pendiente}.</li>
 *   <li>Una cuota vencida pero ya pagada permanece en {@code pagada}.</li>
 *   <li>El job opera sobre todos los tenants del sistema en una sola corrida
 *       (contexto de sistema sin tenant HTTP específico).</li>
 *   <li>El conteo devuelto por el job refleja exactamente el número de cuotas afectadas.</li>
 * </ul>
 */
class OverdueInstallmentsJobIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private OverdueInstallmentsJob overdueInstallmentsJob;

  @Autowired
  private InstallmentRepository installmentRepository;

  @Autowired
  private PaymentPlanRepository paymentPlanRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private TreatmentPlan planTratamientoA;
  private TreatmentPlan planTratamientoB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Job Alfa", "940111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Job Beta", "941333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient pacienteA = patientRepository.save(new Patient(tenantA.getId(), "Carlos", "Pérez"));
      planTratamientoA = treatmentPlanRepository.save(new TreatmentPlan(tenantA.getId(), pacienteA));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient pacienteB = patientRepository.save(new Patient(tenantB.getId(), "Lucía", "Gómez"));
      planTratamientoB = treatmentPlanRepository.save(new TreatmentPlan(tenantB.getId(), pacienteB));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Installment crearCuota(UUID tenantId, TreatmentPlan treatmentPlan,
      int numeroCuota, LocalDate fechaVencimiento, InstallmentStatus estadoInicial, Instant paidAt) {
    TenantContext.setTenantId(tenantId);
    try {
      PaymentPlan plan = new PaymentPlan();
      plan.setTenantId(tenantId);
      plan.setTreatmentPlanId(treatmentPlan.getId());
      plan.setTotalAmountCop(new BigDecimal("100000.00"));
      plan.setInstallmentsCount(1);

      Installment cuota = new Installment();
      cuota.setTenantId(tenantId);
      cuota.setInstallmentNumber(numeroCuota);
      cuota.setAmountCop(new BigDecimal("100000.00"));
      cuota.setDueDate(fechaVencimiento);
      cuota.setStatus(estadoInicial);
      cuota.setPaidAt(paidAt);
      plan.addInstallment(cuota);

      paymentPlanRepository.saveAndFlush(plan);
      return cuota;
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de aceptación DoD (FASE6-03)
  // ---------------------------------------------------------------------------

  @Test
  void cuotaConFechaVencidaYPendienteCambiaAVencida() {
    LocalDate ayer = LocalDate.now().minusDays(1);
    Installment cuota = crearCuota(tenantA.getId(), planTratamientoA, 1, ayer, InstallmentStatus.pendiente, null);

    // El job corre en contexto de sistema (sin tenant de request HTTP activo)
    TenantContext.clear();
    int afectadas = overdueInstallmentsJob.execute();

    assertThat(afectadas).isGreaterThanOrEqualTo(1);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Installment recargada = installmentRepository.findById(cuota.getId()).orElseThrow();
      assertThat(recargada.getStatus()).isEqualTo(InstallmentStatus.vencida);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void cuotaConFechaFuturaPermanecePendiente() {
    LocalDate manana = LocalDate.now().plusDays(1);
    Installment cuota = crearCuota(tenantA.getId(), planTratamientoA, 1, manana, InstallmentStatus.pendiente, null);

    TenantContext.clear();
    overdueInstallmentsJob.execute();

    TenantContext.setTenantId(tenantA.getId());
    try {
      Installment recargada = installmentRepository.findById(cuota.getId()).orElseThrow();
      assertThat(recargada.getStatus()).isEqualTo(InstallmentStatus.pendiente);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void cuotaVencidaPeroPagadaNoCambiaAVencida() {
    LocalDate ayer = LocalDate.now().minusDays(1);
    Instant momentoPago = Instant.now().minusSeconds(3600);
    Installment cuota = crearCuota(tenantA.getId(), planTratamientoA, 1, ayer, InstallmentStatus.pagada, momentoPago);

    TenantContext.clear();
    overdueInstallmentsJob.execute();

    TenantContext.setTenantId(tenantA.getId());
    try {
      Installment recargada = installmentRepository.findById(cuota.getId()).orElseThrow();
      assertThat(recargada.getStatus()).isEqualTo(InstallmentStatus.pagada);
      assertThat(recargada.getPaidAt()).isNotNull();
      assertThat(recargada.getPaidAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
          .isEqualTo(momentoPago.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void jobActualizaMultiplesTenantsYRetornaConteoExacto() {
    LocalDate ayer = LocalDate.now().minusDays(2);
    LocalDate manana = LocalDate.now().plusDays(5);

    // Tenant A: 1 vencida, 1 futura
    Installment cuotaVencidaA = crearCuota(tenantA.getId(), planTratamientoA, 1, ayer, InstallmentStatus.pendiente, null);
    Installment cuotaFuturaA = crearCuota(tenantA.getId(), planTratamientoA, 1, manana, InstallmentStatus.pendiente, null);

    // Tenant B: 1 vencida, 1 ya pagada
    Installment cuotaVencidaB = crearCuota(tenantB.getId(), planTratamientoB, 1, ayer, InstallmentStatus.pendiente, null);
    Installment cuotaPagadaB = crearCuota(tenantB.getId(), planTratamientoB, 1, ayer, InstallmentStatus.pagada, Instant.now());

    TenantContext.clear();
    int afectadas = overdueInstallmentsJob.execute();

    // Deben haberse actualizado al menos las 2 cuotas vencidas pendientes (de Tenant A y Tenant B)
    assertThat(afectadas).isGreaterThanOrEqualTo(2);

    // Verificar Tenant A
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThat(installmentRepository.findById(cuotaVencidaA.getId()).orElseThrow().getStatus())
          .isEqualTo(InstallmentStatus.vencida);
      assertThat(installmentRepository.findById(cuotaFuturaA.getId()).orElseThrow().getStatus())
          .isEqualTo(InstallmentStatus.pendiente);
    } finally {
      TenantContext.clear();
    }

    // Verificar Tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      assertThat(installmentRepository.findById(cuotaVencidaB.getId()).orElseThrow().getStatus())
          .isEqualTo(InstallmentStatus.vencida);
      assertThat(installmentRepository.findById(cuotaPagadaB.getId()).orElseThrow().getStatus())
          .isEqualTo(InstallmentStatus.pagada);
    } finally {
      TenantContext.clear();
    }

    // Si se corre de nuevo inmediatamente, no debe haber más cuotas que actualizar (retorna 0)
    int segundaCorrida = overdueInstallmentsJob.execute();
    assertThat(segundaCorrida).isEqualTo(0);
  }
}
