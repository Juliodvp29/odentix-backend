package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.patient.entity.ClinicalRecord;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.ClinicalRecordRepository;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración para ClinicalRecordRepository (FASE2-05) contra PostgreSQL real
 * vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Persistir y recuperar registros clínicos asociados a un paciente existente.</li>
 *   <li>Orden cronológico descendente por recorded_at.</li>
 *   <li>Aislamiento cross-tenant automático por TenantAwareEntity y defensivo en repositorio.</li>
 * </ul>
 */
class ClinicalRecordRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ClinicalRecordRepository clinicalRecordRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant nuevoTenant(String nombre) {
    return tenantRepository.save(new Tenant(nombre, "900" + Math.abs(nombre.hashCode()) + "-1"));
  }

  private Patient nuevoPaciente(UUID tenantId, String nombres) {
    Patient paciente = new Patient(tenantId, nombres, "Pérez");
    paciente.setDocumentType("CC");
    paciente.setDocumentNumber("DOC-" + UUID.randomUUID());
    return paciente;
  }

  private Patient guardarPacienteComo(UUID tenantId, Patient paciente) {
    TenantContext.setTenantId(tenantId);
    try {
      return patientRepository.save(paciente);
    } finally {
      TenantContext.clear();
    }
  }

  private ClinicalRecord guardarRegistroComo(UUID tenantId, ClinicalRecord registro) {
    TenantContext.setTenantId(tenantId);
    try {
      return clinicalRecordRepository.save(registro);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirYRecuperarClinicalRecord() {
    Tenant tenant = nuevoTenant("Clínica CR " + UUID.randomUUID());
    Patient paciente = guardarPacienteComo(tenant.getId(), nuevoPaciente(tenant.getId(), "Carlos"));

    ClinicalRecord record = new ClinicalRecord(tenant.getId(), paciente);
    record.setChiefComplaint("Dolor en molar inferior derecho");
    record.setAnamnesis("Paciente refiere dolor de 3 días de evolución");
    record.setDiagnosis("Pulpitis irreversible diente 46");
    record.setEvolution("Se realiza apertura cameral y conductometría");
    record.setRecordedAt(Instant.now().minus(1, ChronoUnit.HOURS));

    ClinicalRecord guardado = guardarRegistroComo(tenant.getId(), record);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getTenantId()).isEqualTo(tenant.getId());
    assertThat(guardado.getPatient().getId()).isEqualTo(paciente.getId());
    assertThat(guardado.getChiefComplaint()).isEqualTo("Dolor en molar inferior derecho");
    assertThat(guardado.getAnamnesis()).isEqualTo("Paciente refiere dolor de 3 días de evolución");
    assertThat(guardado.getDiagnosis()).isEqualTo("Pulpitis irreversible diente 46");
    assertThat(guardado.getEvolution()).isEqualTo("Se realiza apertura cameral y conductometría");
    assertThat(guardado.getRecordedAt()).isNotNull();
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    TenantContext.setTenantId(tenant.getId());
    try {
      Optional<ClinicalRecord> recuperado = clinicalRecordRepository.findById(guardado.getId());
      assertThat(recuperado).isPresent();
      assertThat(recuperado.get().getDiagnosis()).isEqualTo("Pulpitis irreversible diente 46");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void listarPorPacienteOrdenadoPorRecordedAtDesc() {
    Tenant tenant = nuevoTenant("Clínica Historial " + UUID.randomUUID());
    Patient paciente = guardarPacienteComo(tenant.getId(), nuevoPaciente(tenant.getId(), "Laura"));

    Instant t0 = Instant.now().minus(2, ChronoUnit.DAYS);
    Instant t1 = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant t2 = Instant.now();

    ClinicalRecord recAntiguo = new ClinicalRecord(tenant.getId(), paciente);
    recAntiguo.setChiefComplaint("Primera cita - Limpieza");
    recAntiguo.setRecordedAt(t0);

    ClinicalRecord recMedio = new ClinicalRecord(tenant.getId(), paciente);
    recMedio.setChiefComplaint("Segunda cita - Resina 14");
    recMedio.setRecordedAt(t1);

    ClinicalRecord recReciente = new ClinicalRecord(tenant.getId(), paciente);
    recReciente.setChiefComplaint("Tercera cita - Control");
    recReciente.setRecordedAt(t2);

    guardarRegistroComo(tenant.getId(), recAntiguo);
    guardarRegistroComo(tenant.getId(), recReciente);
    guardarRegistroComo(tenant.getId(), recMedio);

    TenantContext.setTenantId(tenant.getId());
    try {
      List<ClinicalRecord> historial = clinicalRecordRepository
          .findAllByTenantIdAndPatientIdOrderByRecordedAtDesc(tenant.getId(), paciente.getId());

      assertThat(historial).hasSize(3);
      assertThat(historial.get(0).getChiefComplaint()).isEqualTo("Tercera cita - Control");
      assertThat(historial.get(1).getChiefComplaint()).isEqualTo("Segunda cita - Resina 14");
      assertThat(historial.get(2).getChiefComplaint()).isEqualTo("Primera cita - Limpieza");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantEnClinicalRecord() {
    Tenant tenantA = nuevoTenant("Clínica A " + UUID.randomUUID());
    Tenant tenantB = nuevoTenant("Clínica B " + UUID.randomUUID());

    Patient pacienteA = guardarPacienteComo(tenantA.getId(), nuevoPaciente(tenantA.getId(), "Paciente A"));
    Patient pacienteB = guardarPacienteComo(tenantB.getId(), nuevoPaciente(tenantB.getId(), "Paciente B"));

    ClinicalRecord recA = new ClinicalRecord(tenantA.getId(), pacienteA);
    recA.setChiefComplaint("Consulta A");
    guardarRegistroComo(tenantA.getId(), recA);

    ClinicalRecord recB = new ClinicalRecord(tenantB.getId(), pacienteB);
    recB.setChiefComplaint("Consulta B");
    guardarRegistroComo(tenantB.getId(), recB);

    // Tenant A no debe ver registros de Tenant B con findAll()
    TenantContext.setTenantId(tenantA.getId());
    try {
      List<ClinicalRecord> visiblesA = clinicalRecordRepository.findAll();
      assertThat(visiblesA).hasSize(1);
      assertThat(visiblesA.get(0).getId()).isEqualTo(recA.getId());

      // Búsqueda por ID de registro de otro tenant
      assertThat(clinicalRecordRepository.findById(recB.getId())).isEmpty();

      // Repositorio defensivo explícito con tenantId
      assertThat(clinicalRecordRepository.findByIdAndTenantId(recB.getId(), tenantA.getId())).isEmpty();
      assertThat(clinicalRecordRepository.findAllByTenantIdAndPatientIdOrderByRecordedAtDesc(tenantA.getId(), pacienteB.getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}
