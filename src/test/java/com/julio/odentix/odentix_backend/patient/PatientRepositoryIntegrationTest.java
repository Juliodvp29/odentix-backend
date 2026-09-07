package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para Patient (FASE2-01) contra PostgreSQL real
 * vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: persistir/recuperar, filtro automático de
 * tenant heredado de TenantAwareEntity sin configuración adicional, y
 * aislamiento cross-tenant desde el primer ticket (regla §5.4 de
 * AGENTS.md: no es opcional ni "para después").
 */
class PatientRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant nuevoTenant(String nombre) {
    return tenantRepository.save(new Tenant(nombre, "900" + Math.abs(nombre.hashCode()) + "-1"));
  }

  private Patient nuevoPaciente(UUID tenantId, String documento, String nombres) {
    Patient paciente = new Patient(tenantId, nombres, "Apellido Prueba");
    paciente.setDocumentType("CC");
    paciente.setDocumentNumber(documento);
    paciente.setBirthDate(LocalDate.of(1990, 5, 15));
    paciente.setPhone("3001234567");
    paciente.setEmail("paciente." + UUID.randomUUID() + "@correo.com");
    paciente.setAddress("Calle 123 #45-67, Bogotá");
    paciente.setEmergencyContactName("Contacto Emergencia");
    paciente.setEmergencyContactPhone("3107654321");
    return paciente;
  }

  private Patient guardarComo(UUID tenantId, Patient paciente) {
    TenantContext.setTenantId(tenantId);
    try {
      return patientRepository.save(paciente);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirYRecuperarPatient() {
    Tenant tenant = nuevoTenant("Clínica Persistencia " + UUID.randomUUID());
    Patient guardado = guardarComo(
        tenant.getId(), nuevoPaciente(tenant.getId(), "DOC-" + UUID.randomUUID(), "María"));

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getTenantId()).isEqualTo(tenant.getId());
    assertThat(guardado.getFirstName()).isEqualTo("María");
    assertThat(guardado.getLastName()).isEqualTo("Apellido Prueba");
    assertThat(guardado.getBirthDate()).isEqualTo(LocalDate.of(1990, 5, 15));
    assertThat(guardado.getEmergencyContactName()).isEqualTo("Contacto Emergencia");
    assertThat(guardado.isActive()).isTrue();
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    TenantContext.setTenantId(tenant.getId());
    try {
      Optional<Patient> recuperado = patientRepository.findById(guardado.getId());
      assertThat(recuperado).isPresent();
      assertThat(recuperado.get().getPhone()).isEqualTo("3001234567");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void filtroAutomaticoHeradadoAislaPorTenantSinConfiguracionAdicional() {
    Tenant tenantA = nuevoTenant("Clínica Filtro A " + UUID.randomUUID());
    Tenant tenantB = nuevoTenant("Clínica Filtro B " + UUID.randomUUID());
    Patient pacienteA = guardarComo(
        tenantA.getId(), nuevoPaciente(tenantA.getId(), "DOC-" + UUID.randomUUID(), "Ana"));
    Patient pacienteB = guardarComo(
        tenantB.getId(), nuevoPaciente(tenantB.getId(), "DOC-" + UUID.randomUUID(), "Beto"));

    // Query "ingenua" (sin tenant_id manual): solo ve lo del tenant activo.
    TenantContext.setTenantId(tenantA.getId());
    try {
      List<Patient> visibles = patientRepository.findAll();
      assertThat(visibles).hasSize(1);
      assertThat(visibles.get(0).getId()).isEqualTo(pacienteA.getId());
    } finally {
      TenantContext.clear();
    }

    // Búsqueda por ID directo de otro tenant: invisible (ni siquiera existe).
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThat(patientRepository.findById(pacienteB.getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void metodosFiltradosPorTenantNoVenDatosAjenos() {
    Tenant tenantA = nuevoTenant("Clínica Repo A " + UUID.randomUUID());
    Tenant tenantB = nuevoTenant("Clínica Repo B " + UUID.randomUUID());
    Patient pacienteB = guardarComo(
        tenantB.getId(), nuevoPaciente(tenantB.getId(), "DOC-" + UUID.randomUUID(), "Beto"));

    assertThat(patientRepository.findAllByTenantId(tenantA.getId())).isEmpty();
    assertThat(patientRepository.findAllByTenantId(tenantB.getId()))
        .extracting(Patient::getId)
        .contains(pacienteB.getId());
    assertThat(patientRepository.findByIdAndTenantId(pacienteB.getId(), tenantA.getId())).isEmpty();
    assertThat(patientRepository.findByIdAndTenantId(pacienteB.getId(), tenantB.getId())).isPresent();
  }

  @Test
  void documentoUnicoPorTenantPeroReutilizableEntreTenants() {
    Tenant tenantA = nuevoTenant("Clínica Doc A " + UUID.randomUUID());
    Tenant tenantB = nuevoTenant("Clínica Doc B " + UUID.randomUUID());
    String documento = "DOC-COMPARTIDO-" + UUID.randomUUID();

    TenantContext.setTenantId(tenantA.getId());
    Patient primero;
    try {
      primero = patientRepository.saveAndFlush(nuevoPaciente(tenantA.getId(), documento, "Ana"));
    } finally {
      TenantContext.clear();
    }
    assertThat(primero.getId()).isNotNull();

    // Mismo documento en otro tenant: permitido (el unique es por tenant).
    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient otroTenant = patientRepository.saveAndFlush(nuevoPaciente(tenantB.getId(), documento, "Beto"));
      assertThat(otroTenant.getId()).isNotNull();
    } finally {
      TenantContext.clear();
    }

    // Mismo documento dentro del mismo tenant: constraint de BD, no solo código.
    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(
          () -> patientRepository.saveAndFlush(nuevoPaciente(tenantA.getId(), documento, "Duplicado")))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }
}
