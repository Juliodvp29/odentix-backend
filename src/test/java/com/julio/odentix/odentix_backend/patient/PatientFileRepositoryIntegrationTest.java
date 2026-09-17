package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.entity.PatientFile;
import com.julio.odentix.odentix_backend.patient.repository.PatientFileRepository;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración del repositorio PatientFileRepository (FASE2-09).
 */
class PatientFileRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PatientFileRepository patientFileRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Patient patientA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    tenantA = tenantRepository.save(new Tenant("Clínica Archivos A", "901111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Archivos B", "901333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = new Patient(tenantA.getId(), "Carlos", "Gómez");
      patientA.setDocumentNumber("DOC-REPO-A");
      patientA = patientRepository.save(patientA);
    } finally {
      TenantContext.clear();
    }
  }

  private PatientFile guardarComo(UUID tenantId, PatientFile file) {
    TenantContext.setTenantId(tenantId);
    try {
      return patientFileRepository.save(file);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("Persiste un PatientFile y lo recupera ordenado por fecha de creación")
  void shouldPersistAndRetrievePatientFile() {
    PatientFile file1 = new PatientFile(
        tenantA.getId(),
        patientA,
        "tenants/" + tenantA.getId() + "/patients/" + patientA.getId() + "/1-foto1.jpg",
        "foto1.jpg",
        "image/jpeg",
        1024L,
        null);
    guardarComo(tenantA.getId(), file1);

    PatientFile file2 = new PatientFile(
        tenantA.getId(),
        patientA,
        "tenants/" + tenantA.getId() + "/patients/" + patientA.getId() + "/2-doc.pdf",
        "doc.pdf",
        "application/pdf",
        2048L,
        null);
    guardarComo(tenantA.getId(), file2);

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<PatientFile> files = patientFileRepository.findByPatientIdOrderByCreatedAtDesc(patientA.getId());
      assertThat(files).hasSize(2);
      assertThat(files.get(0).getPatient().getId()).isEqualTo(patientA.getId());
      assertThat(files.get(0).getCreatedAt()).isNotNull();
      assertThat(files.get(0).getUpdatedAt()).isNotNull();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("El filtro @TenantId impide que Tenant B vea los archivos de Tenant A")
  void shouldIsolateFilesCrossTenant() {
    PatientFile fileA = new PatientFile(
        tenantA.getId(),
        patientA,
        "tenants/" + tenantA.getId() + "/patients/" + patientA.getId() + "/secret.pdf",
        "secret.pdf",
        "application/pdf",
        5000L,
        null);
    guardarComo(tenantA.getId(), fileA);

    // Cambiar contexto a Tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      List<PatientFile> filesB = patientFileRepository.findAll();
      assertThat(filesB).isEmpty();

      assertThat(patientFileRepository.findById(fileA.getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}
