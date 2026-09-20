package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntry;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.OdontogramEntryRepository;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para OdontogramEntry (FASE2-07) contra PostgreSQL
 * real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: varias entradas para la misma pieza dental
 * coexisten sin sobrescribirse, más el aislamiento cross-tenant obligatorio
 * (test cross-tenant obligatorio) y el CHECK de notación FDI.
 */
class OdontogramEntryRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private OdontogramEntryRepository odontogramEntryRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Patient patientA;
  private Patient patientB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Odontograma A", "907111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Odontograma B", "908333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Pérez"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Beto", "Gómez"));
    } finally {
      TenantContext.clear();
    }
  }

  private OdontogramEntry nuevaEntrada(
      UUID tenantId, Patient patient, short toothNumber, OdontogramEntryType type, String condition) {
    OdontogramEntry entry = new OdontogramEntry(tenantId, patient, toothNumber, type, condition);
    entry.setSurface("oclusal");
    return entry;
  }

  private OdontogramEntry guardarComo(UUID tenantId, OdontogramEntry entry) {
    TenantContext.setTenantId(tenantId);
    try {
      return odontogramEntryRepository.save(entry);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void variasEntradasMismaPiezaNoSeSobrescriben() {
    OdontogramEntry diagnostico = guardarComo(tenantA.getId(), nuevaEntrada(
        tenantA.getId(), patientA, (short) 16, OdontogramEntryType.diagnostico, "caries"));
    OdontogramEntry tratamiento = guardarComo(tenantA.getId(), nuevaEntrada(
        tenantA.getId(), patientA, (short) 16, OdontogramEntryType.tratamiento_realizado, "obturado"));

    assertThat(diagnostico.getId()).isNotNull();
    assertThat(tratamiento.getId()).isNotNull();
    assertThat(tratamiento.getId()).isNotEqualTo(diagnostico.getId());

    List<OdontogramEntry> entradas = odontogramEntryRepository
        .findAllByTenantIdAndPatientId(tenantA.getId(), patientA.getId());

    // Misma pieza, dos momentos clínicos distintos: ambas recuperables.
    assertThat(entradas).hasSize(2);
    assertThat(entradas)
        .extracting(OdontogramEntry::getEntryType)
        .containsExactlyInAnyOrder(
            OdontogramEntryType.diagnostico, OdontogramEntryType.tratamiento_realizado);
    assertThat(entradas)
        .allSatisfy(e -> {
          assertThat(e.getToothNumber()).isEqualTo((short) 16);
          assertThat(e.getTenantId()).isEqualTo(tenantA.getId());
          assertThat(e.getCreatedAt()).isNotNull();
        });
  }

  @Test
  void queryIngenuaSoloVeEntradasDelTenantActivo() {
    OdontogramEntry entradaA = guardarComo(tenantA.getId(), nuevaEntrada(
        tenantA.getId(), patientA, (short) 16, OdontogramEntryType.estado_actual, "sano"));
    OdontogramEntry entradaB = guardarComo(tenantB.getId(), nuevaEntrada(
        tenantB.getId(), patientB, (short) 16, OdontogramEntryType.diagnostico, "caries"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<OdontogramEntry> visibles = odontogramEntryRepository.findAll();
      assertThat(visibles).hasSize(1);
      assertThat(visibles.get(0).getId()).isEqualTo(entradaA.getId());

      // Búsqueda por ID directo de otro tenant: invisible.
      assertThat(odontogramEntryRepository.findById(entradaB.getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }

    // Métodos con tenantId explícito tampoco ven datos ajenos.
    assertThat(odontogramEntryRepository.findAllByTenantId(tenantA.getId()))
        .extracting(OdontogramEntry::getId)
        .containsExactly(entradaA.getId());
    assertThat(odontogramEntryRepository.findByIdAndTenantId(entradaB.getId(), tenantA.getId()))
        .isEmpty();
  }

  @Test
  void toothNumberFueraDeNotacionFdiViolaConstraintDeBd() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(() -> odontogramEntryRepository.saveAndFlush(nuevaEntrada(
          tenantA.getId(), patientA, (short) 99, OdontogramEntryType.diagnostico, "caries")))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }
}

