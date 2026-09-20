package com.julio.odentix.odentix_backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para {@link PaymentPlan} e {@link Installment} (FASE6-01)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Definir un plan de pago en cuotas y verificar que cada cuota nace en estado
 *       {@code pendiente}.</li>
 *   <li>Verificar la constraint {@code UNIQUE (payment_plan_id, installment_number)}
 *       rechaza cuotas duplicadas en el mismo plan.</li>
 *   <li>Aislamiento cross-tenant: un usuario del tenant B no puede ver ni modificar
 *       el plan de pago del tenant A, incluso conociendo su UUID (regla de aislamiento multi-tenant del proyecto).
 *       La respuesta es {@code empty} (404 a nivel HTTP), no 403 — para no confirmar
 *       que el recurso existe.</li>
 * </ul>
 */
class PaymentPlanRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PaymentPlanRepository paymentPlanRepository;

  @Autowired
  private InstallmentRepository installmentRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private TreatmentPlan planTratamientoA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Cartera Alfa", "920111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Cartera Beta", "921333444-2"));

    // Crear tratamiento base en tenantA (dependencia requerida por la FK).
    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient paciente = patientRepository.save(new Patient(tenantA.getId(), "Pedro", "Ramírez"));
      planTratamientoA = treatmentPlanRepository.save(
          new TreatmentPlan(tenantA.getId(), paciente, null, "Plan de rehabilitación integral"));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------------

  /**
   * Construye un {@link PaymentPlan} con N cuotas iguales y lo persiste como
   * el tenant indicado.
   */
  private PaymentPlan crearPlanConCuotas(UUID tenantId, TreatmentPlan planTratamiento,
      BigDecimal montoTotal, int numeroCuotas) {
    TenantContext.setTenantId(tenantId);
    try {
      PaymentPlan plan = new PaymentPlan();
      plan.setTenantId(tenantId);
      plan.setTreatmentPlanId(planTratamiento.getId());
      plan.setTotalAmountCop(montoTotal);
      plan.setInstallmentsCount(numeroCuotas);

      BigDecimal montoPorCuota = montoTotal.divide(new BigDecimal(numeroCuotas));
      for (int i = 1; i <= numeroCuotas; i++) {
        Installment cuota = new Installment();
        cuota.setTenantId(tenantId);
        cuota.setInstallmentNumber(i);
        cuota.setAmountCop(montoPorCuota);
        cuota.setDueDate(LocalDate.now().plusMonths(i));
        plan.addInstallment(cuota);
      }

      return paymentPlanRepository.saveAndFlush(plan);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE6-01)
  // ---------------------------------------------------------------------------

  @Test
  void planDePagoConCuotasPersistidoConEstadoPendiente() {
    PaymentPlan guardado = crearPlanConCuotas(
        tenantA.getId(), planTratamientoA,
        new BigDecimal("900000.00"), 3);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<PaymentPlan> encontrado = paymentPlanRepository.findById(guardado.getId());
      assertThat(encontrado).isPresent();

      PaymentPlan plan = encontrado.get();
      assertThat(plan.getTreatmentPlanId()).isEqualTo(planTratamientoA.getId());
      assertThat(plan.getTotalAmountCop()).isEqualByComparingTo(new BigDecimal("900000.00"));
      assertThat(plan.getInstallmentsCount()).isEqualTo(3);

      List<Installment> cuotas = installmentRepository
          .findByPaymentPlanIdOrderByInstallmentNumberAsc(plan.getId());
      assertThat(cuotas).hasSize(3);
      // Toda cuota nace en estado pendiente (DoD del ticket).
      assertThat(cuotas).extracting(Installment::getStatus)
          .containsOnly(InstallmentStatus.pendiente);
      // Ordenadas por número de cuota.
      assertThat(cuotas).extracting(Installment::getInstallmentNumber)
          .containsExactly(1, 2, 3);
      // Ningún paidAt registrado todavía.
      assertThat(cuotas).extracting(Installment::getPaidAt).containsOnlyNulls();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void buscarPorTratamientoDevuelvePlanesDelTenantActivo() {
    crearPlanConCuotas(tenantA.getId(), planTratamientoA, new BigDecimal("600000.00"), 2);

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<PaymentPlan> planes = paymentPlanRepository
          .findByTreatmentPlanId(planTratamientoA.getId());
      assertThat(planes).hasSize(1);
      assertThat(planes.get(0).getTreatmentPlanId()).isEqualTo(planTratamientoA.getId());
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Test de constraint UNIQUE (payment_plan_id, installment_number)
  // ---------------------------------------------------------------------------

  @Test
  void cuotaDuplicadaEnMismoPlanFallaConstraint() {
    // Persistir el plan base con una cuota.
    TenantContext.setTenantId(tenantA.getId());
    PaymentPlan planBase;
    try {
      planBase = new PaymentPlan();
      planBase.setTenantId(tenantA.getId());
      planBase.setTreatmentPlanId(planTratamientoA.getId());
      planBase.setTotalAmountCop(new BigDecimal("300000.00"));
      planBase.setInstallmentsCount(1);

      Installment cuota1 = new Installment();
      cuota1.setTenantId(tenantA.getId());
      cuota1.setInstallmentNumber(1);
      cuota1.setAmountCop(new BigDecimal("300000.00"));
      cuota1.setDueDate(LocalDate.now().plusMonths(1));
      planBase.addInstallment(cuota1);

      planBase = paymentPlanRepository.saveAndFlush(planBase);
    } finally {
      TenantContext.clear();
    }

    // Intentar insertar otra cuota con el mismo número → DataIntegrityViolationException.
    final PaymentPlan planFinal = planBase;
    TenantContext.setTenantId(tenantA.getId());
    try {
      Installment cuotaDuplicada = new Installment();
      cuotaDuplicada.setTenantId(tenantA.getId());
      cuotaDuplicada.setInstallmentNumber(1); // duplicado
      cuotaDuplicada.setAmountCop(new BigDecimal("300000.00"));
      cuotaDuplicada.setDueDate(LocalDate.now().plusMonths(2));
      cuotaDuplicada.setPaymentPlan(planFinal);

      assertThatThrownBy(() -> installmentRepository.saveAndFlush(cuotaDuplicada))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Test de aislamiento cross-tenant (regla de aislamiento multi-tenant del proyecto — obligatorio)
  // ---------------------------------------------------------------------------

  @Test
  void tenantBNoPuedeVerPlanDePagoDelTenantA() {
    PaymentPlan planA = crearPlanConCuotas(
        tenantA.getId(), planTratamientoA,
        new BigDecimal("600000.00"), 2);
    UUID idPlanA = planA.getId();

    // Autenticado como tenantB, el mismo UUID es invisible (filtro @TenantId).
    TenantContext.setTenantId(tenantB.getId());
    try {
      Optional<PaymentPlan> resultado = paymentPlanRepository.findById(idPlanA);
      // Debe ser vacío — no 403, sino como si no existiera .
      assertThat(resultado).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void tenantBNoPuedeVerCuotasDelTenantA() {
    PaymentPlan planA = crearPlanConCuotas(
        tenantA.getId(), planTratamientoA,
        new BigDecimal("400000.00"), 2);

    // Autenticado como tenantB: buscar cuotas del plan de tenantA devuelve vacío.
    TenantContext.setTenantId(tenantB.getId());
    try {
      List<Installment> cuotas = installmentRepository
          .findByPaymentPlanIdOrderByInstallmentNumberAsc(planA.getId());
      assertThat(cuotas).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}

