package com.julio.odentix.odentix_backend.opportunity;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.opportunity.service.TreatmentPlanFollowupJob;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración para {@link TreatmentPlanFollowupJob} (FASE9-01)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Existe al menos una regla completa funcionando de punta a punta.</li>
 *   <li>Correr el job dos veces seguidas no duplica la misma oportunidad
 *       ya abierta para el mismo tratamiento.</li>
 *   <li>Aislamiento cross-tenant: las oportunidades heredan el tenant de su plan.</li>
 * </ul>
 */
class TreatmentPlanFollowupJobIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TreatmentPlanFollowupJob job;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private OpportunityRepository opportunityRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Oportunidad Alfa", "990111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Oportunidad Beta", "991333444-2"));
  }

  /**
   * Escenario principal: un plan 'presentado' con más de 3 días sin contacto
   * genera una oportunidad de tipo 'tratamiento_sin_seguimiento'.
   */
  @Test
  void detectaPlanSinSeguimientoYGeneraOportunidad() {
    // Arrange: plan presentado hace 5 días sin contacto reciente
    TenantContext.setTenantId(tenantA.getId());
    TreatmentPlan plan = crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.presentado, 5, new BigDecimal("800000.00"));
    TenantContext.clear();

    // Act
    int creadas = job.execute();

    // Assert
    assertThat(creadas).isEqualTo(1);

    TenantContext.setTenantId(tenantA.getId());
    List<Opportunity> oportunidades = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantA.getId());
    TenantContext.clear();

    assertThat(oportunidades).hasSize(1);
    Opportunity op = oportunidades.get(0);
    assertThat(op.getType()).isEqualTo(OpportunityType.tratamiento_sin_seguimiento);
    assertThat(op.getRelatedEntityType()).isEqualTo("treatment_plan");
    assertThat(op.getRelatedEntityId()).isEqualTo(plan.getId());
    assertThat(op.getStatus()).isEqualTo(OpportunityStatus.abierta);
    assertThat(op.getEstimatedValueCop()).isEqualByComparingTo(new BigDecimal("800000.00"));
    assertThat(op.getPriority()).isEqualTo((short) 3); // ≥ 500k → prioridad 3
    assertThat(op.getTenantId()).isEqualTo(tenantA.getId());
  }

  /**
   * Planes en estado 'en_decision' también son candidatos.
   */
  @Test
  void detectaPlanEnDecisionSinSeguimiento() {
    TenantContext.setTenantId(tenantA.getId());
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.en_decision, 4, new BigDecimal("2500000.00"));
    TenantContext.clear();

    int creadas = job.execute();

    assertThat(creadas).isEqualTo(1);

    TenantContext.setTenantId(tenantA.getId());
    List<Opportunity> oportunidades = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantA.getId());
    TenantContext.clear();

    assertThat(oportunidades).hasSize(1);
    assertThat(oportunidades.get(0).getPriority()).isEqualTo((short) 4); // ≥ 2M → prioridad 4
  }

  /**
   * Plan reciente (menos de 3 días) NO genera oportunidad.
   */
  @Test
  void noCreaOportunidadParaPlanReciente() {
    TenantContext.setTenantId(tenantA.getId());
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.presentado, 1, new BigDecimal("500000.00"));
    TenantContext.clear();

    int creadas = job.execute();

    assertThat(creadas).isZero();
  }

  /**
   * Planes en estados terminales (aceptado, rechazado, completado, etc.) NO son candidatos.
   */
  @Test
  void noCreaOportunidadParaPlanEnEstadoTerminal() {
    TenantContext.setTenantId(tenantA.getId());
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.aceptado, 10, new BigDecimal("1000000.00"));
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.rechazado, 10, new BigDecimal("1000000.00"));
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.completado, 10, new BigDecimal("1000000.00"));
    TenantContext.clear();

    int creadas = job.execute();

    assertThat(creadas).isZero();
  }

  /**
   * Idempotencia: ejecutar el job 2 veces consecutivas NO duplica la oportunidad.
   */
  @Test
  void idempotencia_dobleEjecucionNoDuplica() {
    TenantContext.setTenantId(tenantA.getId());
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.presentado, 5, new BigDecimal("100000.00"));
    TenantContext.clear();

    int primeraVez = job.execute();
    int segundaVez = job.execute();

    assertThat(primeraVez).isEqualTo(1);
    assertThat(segundaVez).isZero();

    TenantContext.setTenantId(tenantA.getId());
    List<Opportunity> oportunidades = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantA.getId());
    TenantContext.clear();

    assertThat(oportunidades).hasSize(1);
  }

  /**
   * Aislamiento cross-tenant: el job crea oportunidades para ambos tenants,
   * cada una con el tenantId correcto.
   */
  @Test
  void aislamientoCrossTenant() {
    TenantContext.setTenantId(tenantA.getId());
    crearPlanConAntigüedad(
        tenantA, TreatmentPlanStatus.presentado, 5, new BigDecimal("600000.00"));
    TenantContext.clear();

    TenantContext.setTenantId(tenantB.getId());
    crearPlanConAntigüedad(
        tenantB, TreatmentPlanStatus.en_decision, 7, new BigDecimal("3000000.00"));
    TenantContext.clear();

    int creadas = job.execute();
    assertThat(creadas).isEqualTo(2);

    // Verificar que cada tenant solo ve sus oportunidades
    TenantContext.setTenantId(tenantA.getId());
    List<Opportunity> opA = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantA.getId());
    TenantContext.clear();

    assertThat(opA).hasSize(1);
    assertThat(opA.get(0).getTenantId()).isEqualTo(tenantA.getId());
    assertThat(opA.get(0).getPriority()).isEqualTo((short) 3); // 600k → prioridad 3

    TenantContext.setTenantId(tenantB.getId());
    List<Opportunity> opB = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantB.getId());
    TenantContext.clear();

    assertThat(opB).hasSize(1);
    assertThat(opB.get(0).getTenantId()).isEqualTo(tenantB.getId());
    assertThat(opB.get(0).getPriority()).isEqualTo((short) 4); // 3M → prioridad 4
  }

  // --- Helpers ---

  /**
   * Crea un plan de tratamiento con una antigüedad determinada para simular
   * que no ha tenido contacto reciente. Usa {@code createdAt} como fecha de
   * referencia (el COALESCE de la query prioriza lastContactAt > presentedAt > createdAt).
   */
  private TreatmentPlan crearPlanConAntigüedad(
      Tenant tenant, TreatmentPlanStatus status, int diasDeAntigüedad,
      BigDecimal totalPriceCop) {

    Patient paciente = patientRepository.save(new Patient(tenant.getId(), "Paciente", "Test"));
    Professional profesional = professionalRepository.saveAndFlush(
        new Professional(tenant.getId(), "Dr. Test"));

    TreatmentPlan plan = new TreatmentPlan(tenant.getId(), paciente);
    plan.setProfessional(profesional);
    plan.setStatus(status);
    plan.setTotalPriceCop(totalPriceCop);

    // Simular antigüedad: setear createdAt manualmente (no se puede vía constructor
    // porque @PrePersist lo sobreescribe, así que guardamos y luego actualizamos
    // directamente en BD).
    plan = treatmentPlanRepository.saveAndFlush(plan);

    // Actualizar la fecha directamente para simular antigüedad
    Instant antiguo = Instant.now().minus(diasDeAntigüedad, ChronoUnit.DAYS);
    plan.setCreatedAt(antiguo);
    // Si el plan está presentado, también poner presentedAt en esa fecha
    if (status == TreatmentPlanStatus.presentado || status == TreatmentPlanStatus.en_decision) {
      plan.setPresentedAt(antiguo);
    }
    return treatmentPlanRepository.saveAndFlush(plan);
  }
}
