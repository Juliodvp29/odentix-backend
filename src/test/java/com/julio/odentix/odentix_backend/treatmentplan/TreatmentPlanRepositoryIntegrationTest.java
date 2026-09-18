package com.julio.odentix.odentix_backend.treatmentplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanItemRepository;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

/**
 * Pruebas de integración para {@link TreatmentPlan} y {@link TreatmentPlanItem} (FASE4-01)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Persistir un plan de tratamiento con varios ítems asociados en cascada.</li>
 *   <li>Recuperar el plan con sus ítems vía consulta fetch join.</li>
 *   <li>Aislamiento multi-tenant estricto (regla §5 de AGENTS.md).</li>
 *   <li>Trigger de consistencia de tenant para paciente y profesional.</li>
 *   <li>Restricción de notación FDI para números de diente (11 a 48).</li>
 *   <li>Soporte de orphan removal al desasociar un ítem del plan.</li>
 * </ul>
 */
class TreatmentPlanRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private TreatmentPlanItemRepository treatmentPlanItemRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Patient patientA;
  private Patient patientB;
  private Professional professionalA;
  private Professional professionalB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Dental Alfa", "901111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Dental Beta", "902333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Carlos", "Sánchez"));
      professionalA = professionalRepository.save(new Professional(tenantA.getId(), "Dr. Mario Odontólogo"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Luigi", "Sánchez"));
      professionalB = professionalRepository.save(new Professional(tenantB.getId(), "Dr. Luigi Odontólogo"));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirPlanConItemsEnCascadaExitoso() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), patientA, professionalA, "Rehabilitación del sector posterior");

      TreatmentPlanItem item1 = new TreatmentPlanItem(
          tenantA.getId(), plan, null, (short) 16, new BigDecimal("150000.00"), new BigDecimal("10000.00"));
      TreatmentPlanItem item2 = new TreatmentPlanItem(
          tenantA.getId(), plan, null, (short) 21, new BigDecimal("250000.00"), BigDecimal.ZERO);

      plan.addItem(item1);
      plan.addItem(item2);

      assertThat(plan.getTotalPriceCop()).isEqualByComparingTo(new BigDecimal("390000.00"));

      TreatmentPlan guardado = treatmentPlanRepository.saveAndFlush(plan);

      assertThat(guardado.getId()).isNotNull();
      assertThat(guardado.getStatus()).isEqualTo(TreatmentPlanStatus.borrador);
      assertThat(guardado.getTotalPriceCop()).isEqualByComparingTo(new BigDecimal("390000.00"));
      assertThat(guardado.getItems()).hasSize(2);

      List<TreatmentPlanItem> itemsEnBd = treatmentPlanItemRepository.findByTreatmentPlanIdAndTenantId(guardado.getId(), tenantA.getId());
      assertThat(itemsEnBd).hasSize(2);
      assertThat(itemsEnBd).extracting(TreatmentPlanItem::getToothNumber).containsExactlyInAnyOrder((short) 16, (short) 21);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void recuperarPlanConItemsMedianteFetchJoin() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), patientA, professionalA, "Endodoncia y corona");
      TreatmentPlanItem item = new TreatmentPlanItem(tenantA.getId(), plan, null, (short) 36, new BigDecimal("450000.00"), BigDecimal.ZERO);
      plan.addItem(item);

      TreatmentPlan guardado = treatmentPlanRepository.saveAndFlush(plan);

      Optional<TreatmentPlan> recuperado = treatmentPlanRepository.findWithItemsByIdAndTenantId(guardado.getId(), tenantA.getId());
      assertThat(recuperado).isPresent();
      assertThat(recuperado.get().getItems()).hasSize(1);
      assertThat(recuperado.get().getItems().getFirst().getToothNumber()).isEqualTo((short) 36);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantEstricto() {
    UUID planId;
    UUID itemId;

    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), patientA, professionalA, "Plan privado Alfa");
      TreatmentPlanItem item = new TreatmentPlanItem(tenantA.getId(), plan, null, (short) 11, new BigDecimal("120000.00"), BigDecimal.ZERO);
      plan.addItem(item);

      TreatmentPlan guardado = treatmentPlanRepository.saveAndFlush(plan);
      planId = guardado.getId();
      itemId = guardado.getItems().getFirst().getId();
    } finally {
      TenantContext.clear();
    }

    // Ahora cambiamos al contexto del tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      Optional<TreatmentPlan> planDesdeB = treatmentPlanRepository.findByIdAndTenantId(planId, tenantB.getId());
      assertThat(planDesdeB).isEmpty();

      List<TreatmentPlan> planesPacienteDesdeB = treatmentPlanRepository.findByPatientIdAndTenantId(patientA.getId(), tenantB.getId());
      assertThat(planesPacienteDesdeB).isEmpty();

      Optional<TreatmentPlanItem> itemDesdeB = treatmentPlanItemRepository.findByIdAndTenantId(itemId, tenantB.getId());
      assertThat(itemDesdeB).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void fallarAlAsociarPacienteDeOtroTenant() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      // Intentamos crear un plan para tenantA pero con paciente de tenantB
      TreatmentPlan planInvalido = new TreatmentPlan(tenantA.getId(), patientB, professionalA, "Intento cross-tenant");
      assertThatThrownBy(() -> treatmentPlanRepository.saveAndFlush(planInvalido))
          .isInstanceOf(JpaSystemException.class)
          .hasMessageContaining("no pertenece al tenant");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void fallarAlAsociarProfesionalDeOtroTenant() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      // Intentamos crear un plan para tenantA pero con profesional de tenantB
      TreatmentPlan planInvalido = new TreatmentPlan(tenantA.getId(), patientA, professionalB, "Intento cross-tenant profesional");
      assertThatThrownBy(() -> treatmentPlanRepository.saveAndFlush(planInvalido))
          .isInstanceOf(JpaSystemException.class)
          .hasMessageContaining("no pertenece al tenant");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void fallarAlInsertarPiezaDentalFueraDeRangoFDI() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), patientA, professionalA, "Diente inexistente");
      // Diente 99 no existe en notación FDI (rango 11-48)
      TreatmentPlanItem itemInvalido = new TreatmentPlanItem(tenantA.getId(), plan, null, (short) 99, new BigDecimal("100000.00"), BigDecimal.ZERO);
      plan.addItem(itemInvalido);

      assertThatThrownBy(() -> treatmentPlanRepository.saveAndFlush(plan))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void soporteOrphanRemoval() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      TreatmentPlan plan = new TreatmentPlan(tenantA.getId(), patientA, professionalA, "Plan con 2 tratamientos");
      TreatmentPlanItem item1 = new TreatmentPlanItem(tenantA.getId(), plan, null, (short) 14, new BigDecimal("80000.00"), BigDecimal.ZERO);
      TreatmentPlanItem item2 = new TreatmentPlanItem(tenantA.getId(), plan, null, (short) 15, new BigDecimal("90000.00"), BigDecimal.ZERO);
      plan.addItem(item1);
      plan.addItem(item2);

      TreatmentPlan guardado = treatmentPlanRepository.saveAndFlush(plan);
      assertThat(guardado.getItems()).hasSize(2);
      UUID item1Id = guardado.getItems().getFirst().getId();

      // Removemos el primer ítem
      guardado.removeItem(guardado.getItems().getFirst());
      treatmentPlanRepository.saveAndFlush(guardado);

      assertThat(guardado.getItems()).hasSize(1);
      assertThat(guardado.getTotalPriceCop()).isEqualByComparingTo(new BigDecimal("90000.00"));

      // Verificamos que se haya eliminado de la base de datos
      Optional<TreatmentPlanItem> itemEliminado = treatmentPlanItemRepository.findByIdAndTenantId(item1Id, tenantA.getId());
      assertThat(itemEliminado).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}
