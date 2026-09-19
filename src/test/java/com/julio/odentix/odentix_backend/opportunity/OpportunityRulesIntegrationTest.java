package com.julio.odentix.odentix_backend.opportunity;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.entity.StockMovement;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.inventory.repository.StockMovementRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.opportunity.service.CriticalInventoryJob;
import com.julio.odentix.odentix_backend.opportunity.service.HighRiskAppointmentJob;
import com.julio.odentix.odentix_backend.opportunity.service.InactivePatientJob;
import com.julio.odentix.odentix_backend.opportunity.service.LeadUnrespondedJob;
import com.julio.odentix.odentix_backend.opportunity.service.OverdueInstallmentOpportunityJob;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de las 5 reglas por job del motor de oportunidades (FASE9-02)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cada test genera el escenario de su regla y verifica la oportunidad
 * (tipo, prioridad, valor y referencia), la idempotencia en segunda corrida y
 * los casos que NO deben generar. La regla por evento (`espacio_disponible`)
 * vive en {@code SlotOpportunityListenerIntegrationTest}.
 *
 * <p>La BD se comparte entre suites y los jobs son globales: todas las
 * aserciones se acotan a las entidades propias del test (nunca a conteos
 * globales), y cada test usa tenants frescos (lección de FASE8-02).
 */
class OpportunityRulesIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private LeadUnrespondedJob leadUnrespondedJob;

  @Autowired
  private HighRiskAppointmentJob highRiskAppointmentJob;

  @Autowired
  private InactivePatientJob inactivePatientJob;

  @Autowired
  private OverdueInstallmentOpportunityJob overdueInstallmentOpportunityJob;

  @Autowired
  private CriticalInventoryJob criticalInventoryJob;

  @Autowired
  private OpportunityRepository opportunityRepository;

  @Autowired
  private LeadRepository leadRepository;

  @Autowired
  private AppointmentRepository appointmentRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private PaymentPlanRepository paymentPlanRepository;

  @Autowired
  private InstallmentRepository installmentRepository;

  @Autowired
  private InventoryItemRepository inventoryItemRepository;

  @Autowired
  private StockMovementRepository stockMovementRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Oportunidades Alfa", "9E0111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Oportunidades Beta", "9E0133444-2"));
  }

  // ---------------------------------------------------------------------------
  // Helpers: las oportunidades se buscan por entidad relacionada dentro del
  // tenant del test (los jobs globales pueden crear otras en paralelo).
  // ---------------------------------------------------------------------------

  private List<Opportunity> oppsPara(UUID tenantId, UUID relatedId) {
    TenantContext.setTenantId(tenantId);
    try {
      return opportunityRepository.findAll().stream()
          .filter(o -> relatedId.equals(o.getRelatedEntityId()))
          .toList();
    } finally {
      TenantContext.clear();
    }
  }

  private void ejecutarJobs() {
    TenantContext.clear();
    leadUnrespondedJob.execute();
    highRiskAppointmentJob.execute();
    inactivePatientJob.execute();
    overdueInstallmentOpportunityJob.execute();
    criticalInventoryJob.execute();
    TenantContext.clear();
  }

  // ---------------------------------------------------------------------------
  // Regla 1: lead_sin_respuesta
  // ---------------------------------------------------------------------------

  @Test
  void leadAntiguoSinRespuestaGeneraOportunidad() {
    Lead antiguo;
    Lead reciente;
    Lead contactado;
    TenantContext.setTenantId(tenantA.getId());
    try {
      antiguo = new Lead(tenantA.getId(), "Viejo Contacto", "3001112233", "viejo@x.com", "instagram");
      antiguo.setEstimatedValueCop(new BigDecimal("1200000.00"));
      antiguo.setCreatedAt(Instant.now().minus(30, ChronoUnit.HOURS));
      antiguo = leadRepository.saveAndFlush(antiguo);

      reciente = leadRepository.saveAndFlush(
          new Lead(tenantA.getId(), "Nuevo Contacto", "3004445566", "nuevo@x.com", "web"));

      contactado = new Lead(tenantA.getId(), "Ya Hablado", "3007778899", "hablado@x.com", null);
      contactado.setStatus(LeadStatus.contactado);
      contactado.setCreatedAt(Instant.now().minus(50, ChronoUnit.HOURS));
      contactado = leadRepository.saveAndFlush(contactado);
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    // Antiguo con valor y fuente → prioridad 4 y valor del lead.
    List<Opportunity> opps = oppsPara(tenantA.getId(), antiguo.getId());
    assertThat(opps).hasSize(1);
    assertThat(opps.get(0).getType()).isEqualTo(OpportunityType.lead_sin_respuesta);
    assertThat(opps.get(0).getPriority()).isEqualTo((short) 4);
    assertThat(opps.get(0).getEstimatedValueCop())
        .isEqualByComparingTo(new BigDecimal("1200000.00"));
    assertThat(opps.get(0).getRelatedEntityType()).isEqualTo("lead");
    assertThat(opps.get(0).getTenantId()).isEqualTo(tenantA.getId());

    // Reciente y contactado → nada.
    assertThat(oppsPara(tenantA.getId(), reciente.getId())).isEmpty();
    assertThat(oppsPara(tenantA.getId(), contactado.getId())).isEmpty();

    // Idempotencia: segunda corrida no duplica.
    ejecutarJobs();
    assertThat(oppsPara(tenantA.getId(), antiguo.getId())).hasSize(1);
  }

  // ---------------------------------------------------------------------------
  // Regla 2: cita_alto_riesgo
  // ---------------------------------------------------------------------------

  @Test
  void citaProximaDeAltoRiesgoGeneraOportunidad() {
    Appointment candidata;
    Appointment riesgoMedio;
    Appointment lejana;
    Appointment confirmada;
    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient paciente = patientRepository.save(new Patient(tenantA.getId(), "Riesgo", "Alto"));
      Professional profesional = professionalRepository.saveAndFlush(
          new Professional(tenantA.getId(), "Dr. Riesgo"));
      Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

      candidata = nuevaCita(paciente, profesional, ahora.plus(10, ChronoUnit.HOURS),
          RiskLevel.alto, AppointmentStatus.programada, new BigDecimal("750000.00"));
      riesgoMedio = nuevaCita(paciente, profesional, ahora.plus(11, ChronoUnit.HOURS),
          RiskLevel.medio, AppointmentStatus.programada, new BigDecimal("750000.00"));
      lejana = nuevaCita(paciente, profesional, ahora.plus(72, ChronoUnit.HOURS),
          RiskLevel.alto, AppointmentStatus.programada, new BigDecimal("750000.00"));
      confirmada = nuevaCita(paciente, profesional, ahora.plus(12, ChronoUnit.HOURS),
          RiskLevel.alto, AppointmentStatus.confirmada, new BigDecimal("750000.00"));
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    List<Opportunity> opps = oppsPara(tenantA.getId(), candidata.getId());
    assertThat(opps).hasSize(1);
    assertThat(opps.get(0).getType()).isEqualTo(OpportunityType.cita_alto_riesgo);
    assertThat(opps.get(0).getPriority()).isEqualTo((short) 4);
    assertThat(opps.get(0).getEstimatedValueCop())
        .isEqualByComparingTo(new BigDecimal("750000.00"));

    assertThat(oppsPara(tenantA.getId(), riesgoMedio.getId())).isEmpty();
    assertThat(oppsPara(tenantA.getId(), lejana.getId())).isEmpty();
    assertThat(oppsPara(tenantA.getId(), confirmada.getId())).isEmpty();

    ejecutarJobs();
    assertThat(oppsPara(tenantA.getId(), candidata.getId())).hasSize(1);
  }

  private Appointment nuevaCita(Patient paciente, Professional profesional, Instant inicio,
      RiskLevel riesgo, AppointmentStatus estado, BigDecimal valor) {
    Appointment cita = new Appointment(tenantA.getId(), paciente, profesional,
        inicio, inicio.plus(1, ChronoUnit.HOURS));
    cita.setRiskLevel(riesgo);
    cita.setStatus(estado);
    cita.setEstimatedValueCop(valor);
    return appointmentRepository.saveAndFlush(cita);
  }

  // ---------------------------------------------------------------------------
  // Regla 4: paciente_inactivo
  // ---------------------------------------------------------------------------

  @Test
  void pacienteSinInteraccionesGeneraOportunidad() {
    Patient inactivo;
    Patient conCita;
    Patient conPlan;
    TenantContext.setTenantId(tenantA.getId());
    try {
      inactivo = patientRepository.save(new Patient(tenantA.getId(), "Olvidado", "Pérez"));
      conCita = patientRepository.save(new Patient(tenantA.getId(), "Activo", "Cita"));
      conPlan = patientRepository.save(new Patient(tenantA.getId(), "Activo", "Plan"));
      Professional profesional = professionalRepository.saveAndFlush(
          new Professional(tenantA.getId(), "Dr. Memoria"));
      Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);

      Appointment cita = new Appointment(tenantA.getId(), conCita, profesional,
          ahora.plus(5, ChronoUnit.HOURS), ahora.plus(6, ChronoUnit.HOURS));
      appointmentRepository.saveAndFlush(cita);
      treatmentPlanRepository.saveAndFlush(new TreatmentPlan(tenantA.getId(), conPlan));
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    List<Opportunity> opps = oppsPara(tenantA.getId(), inactivo.getId());
    assertThat(opps).hasSize(1);
    assertThat(opps.get(0).getType()).isEqualTo(OpportunityType.paciente_inactivo);
    assertThat(opps.get(0).getPriority()).isEqualTo((short) 2);
    assertThat(opps.get(0).getEstimatedValueCop()).isEqualByComparingTo(BigDecimal.ZERO);

    assertThat(oppsPara(tenantA.getId(), conCita.getId())).isEmpty();
    assertThat(oppsPara(tenantA.getId(), conPlan.getId())).isEmpty();

    ejecutarJobs();
    assertThat(oppsPara(tenantA.getId(), inactivo.getId())).hasSize(1);
  }

  // ---------------------------------------------------------------------------
  // Regla 5: saldo_vencido
  // ---------------------------------------------------------------------------

  @Test
  void cuotaVencidaGeneraOportunidadConMonto() {
    Installment grande;
    Installment pequena;
    Installment pendiente;
    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient paciente = patientRepository.save(new Patient(tenantA.getId(), "Deudor", "Mora"));
      TreatmentPlan plan = treatmentPlanRepository.saveAndFlush(
          new TreatmentPlan(tenantA.getId(), paciente));

      grande = nuevaCuota(plan, new BigDecimal("600000.00"), InstallmentStatus.vencida);
      pequena = nuevaCuota(plan, new BigDecimal("200000.00"), InstallmentStatus.vencida);
      pendiente = nuevaCuota(plan, new BigDecimal("900000.00"), InstallmentStatus.pendiente);
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    List<Opportunity> oppsGrande = oppsPara(tenantA.getId(), grande.getId());
    assertThat(oppsGrande).hasSize(1);
    assertThat(oppsGrande.get(0).getType()).isEqualTo(OpportunityType.saldo_vencido);
    assertThat(oppsGrande.get(0).getPriority()).isEqualTo((short) 4);
    assertThat(oppsGrande.get(0).getEstimatedValueCop())
        .isEqualByComparingTo(new BigDecimal("600000.00"));

    List<Opportunity> oppsPequena = oppsPara(tenantA.getId(), pequena.getId());
    assertThat(oppsPequena).hasSize(1);
    assertThat(oppsPequena.get(0).getPriority()).isEqualTo((short) 3);

    assertThat(oppsPara(tenantA.getId(), pendiente.getId())).isEmpty();

    ejecutarJobs();
    assertThat(oppsPara(tenantA.getId(), grande.getId())).hasSize(1);
  }

  private Installment nuevaCuota(TreatmentPlan plan, BigDecimal monto, InstallmentStatus estado) {
    PaymentPlan planPago = new PaymentPlan();
    planPago.setTenantId(tenantA.getId());
    planPago.setTreatmentPlanId(plan.getId());
    planPago.setTotalAmountCop(monto);
    planPago.setInstallmentsCount(1);
    Installment cuota = new Installment();
    cuota.setTenantId(tenantA.getId());
    cuota.setInstallmentNumber(1);
    cuota.setAmountCop(monto);
    cuota.setDueDate(LocalDate.now().minusDays(5));
    cuota.setStatus(estado);
    planPago.addInstallment(cuota);
    return paymentPlanRepository.saveAndFlush(planPago).getInstallments().get(0);
  }

  // ---------------------------------------------------------------------------
  // Regla 6: inventario_critico
  // ---------------------------------------------------------------------------

  @Test
  void itemCriticoGeneraOportunidad() {
    InventoryItem quebrado;
    InventoryItem bajo;
    InventoryItem sano;
    TenantContext.setTenantId(tenantA.getId());
    try {
      quebrado = nuevoItem("Anestesia Q", 5);
      bajo = nuevoItem("Guantes B", 5);
      moverStock(bajo, 3, null);
      sano = nuevoItem("Algodón S", 5);
      moverStock(sano, 50, null);
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    // Quiebre total (0) → prioridad 4.
    List<Opportunity> oppsQuebrado = oppsPara(tenantA.getId(), quebrado.getId());
    assertThat(oppsQuebrado).hasSize(1);
    assertThat(oppsQuebrado.get(0).getType()).isEqualTo(OpportunityType.inventario_critico);
    assertThat(oppsQuebrado.get(0).getPriority()).isEqualTo((short) 4);
    assertThat(oppsQuebrado.get(0).getEstimatedValueCop()).isEqualByComparingTo(BigDecimal.ZERO);

    // Bajo umbral (3 <= 5) → prioridad 3.
    List<Opportunity> oppsBajo = oppsPara(tenantA.getId(), bajo.getId());
    assertThat(oppsBajo).hasSize(1);
    assertThat(oppsBajo.get(0).getPriority()).isEqualTo((short) 3);

    assertThat(oppsPara(tenantA.getId(), sano.getId())).isEmpty();

    ejecutarJobs();
    assertThat(oppsPara(tenantA.getId(), quebrado.getId())).hasSize(1);
  }

  private InventoryItem nuevoItem(String nombre, int umbral) {
    InventoryItem item = new InventoryItem(tenantA.getId(), nombre);
    item.setUnit("unidad");
    item.setMinThreshold(umbral);
    return inventoryItemRepository.saveAndFlush(item);
  }

  private void moverStock(InventoryItem item, int delta, String motivo) {
    StockMovement movement = new StockMovement(tenantA.getId(), item, delta);
    movement.setReason(motivo);
    stockMovementRepository.saveAndFlush(movement);
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant del motor
  // ---------------------------------------------------------------------------

  @Test
  void oportunidadesHeredanTenantSinFiltrarse() {
    Lead leadB;
    TenantContext.setTenantId(tenantB.getId());
    try {
      leadB = new Lead(tenantB.getId(), "Lead Beta", "3009998877", "beta@x.com", "referido");
      leadB.setCreatedAt(Instant.now().minus(30, ChronoUnit.HOURS));
      leadB = leadRepository.saveAndFlush(leadB);
    } finally {
      TenantContext.clear();
    }

    ejecutarJobs();

    // Visible en B con su tenant…
    List<Opportunity> oppsB = oppsPara(tenantB.getId(), leadB.getId());
    assertThat(oppsB).hasSize(1);
    assertThat(oppsB.get(0).getTenantId()).isEqualTo(tenantB.getId());

    // …e invisible desde A (filtro @TenantId, 404 a nivel HTTP).
    assertThat(oppsPara(tenantA.getId(), leadB.getId())).isEmpty();
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThat(opportunityRepository.findById(oppsB.get(0).getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}
