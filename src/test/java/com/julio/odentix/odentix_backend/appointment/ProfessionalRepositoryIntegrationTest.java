package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.patient.entity.ClinicalRecord;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.ClinicalRecordRepository;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para Professional (FASE3-01) contra PostgreSQL
 * real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Persistir y recuperar un {@link Professional} sin usuario (ej. especialista externo).</li>
 *   <li>Persistir y vincular opcionalmente un {@link Professional} a un {@link User} existente.</li>
 *   <li>Aislamiento cross-tenant estricto (regla de aislamiento multi-tenant del proyecto).</li>
 *   <li>Restricción de unicidad de usuario (un usuario a lo sumo un profesional).</li>
 *   <li>Integridad referencial con {@link ClinicalRecord} habilitada por la migración V11.</li>
 * </ul>
 */
class ProfessionalRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private ClinicalRecordRepository clinicalRecordRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private User userA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Dental Alfa", "901111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Dental Beta", "902333444-2"));

    userA = new User(
        tenantA,
        "dr.perez@alfa.com",
        "hash_seguro",
        "Dr. Carlos Pérez",
        UserRole.odontologo
    );
    userA = userRepository.save(userA);
  }

  private Professional guardarComo(UUID tenantId, Professional professional) {
    TenantContext.setTenantId(tenantId);
    try {
      return professionalRepository.save(professional);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirProfessionalSinUsuarioExitoso() {
    Professional externo = new Professional(tenantA.getId(), "Dra. Laura Ortodoncista");
    externo.setSpecialty("Ortodoncia");
    externo.setLicenseNumber("TP-998877");
    externo.setExternal(true);

    Professional guardado = guardarComo(tenantA.getId(), externo);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getUser()).isNull();
    assertThat(guardado.getFullName()).isEqualTo("Dra. Laura Ortodoncista");
    assertThat(guardado.getSpecialty()).isEqualTo("Ortodoncia");
    assertThat(guardado.getLicenseNumber()).isEqualTo("TP-998877");
    assertThat(guardado.isExternal()).isTrue();
    assertThat(guardado.isActive()).isTrue();
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<Professional> recuperado = professionalRepository.findById(guardado.getId());
      assertThat(recuperado).isPresent();
      assertThat(recuperado.get().getFullName()).isEqualTo("Dra. Laura Ortodoncista");
      assertThat(recuperado.get().getUser()).isNull();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirProfessionalConUsuarioAsociadoExitoso() {
    Professional odontologoPlanta = new Professional(tenantA.getId(), "Dr. Carlos Pérez");
    odontologoPlanta.setUser(userA);
    odontologoPlanta.setSpecialty("Odontología General");
    odontologoPlanta.setLicenseNumber("OD-12345");
    odontologoPlanta.setExternal(false);

    Professional guardado = guardarComo(tenantA.getId(), odontologoPlanta);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getUser()).isNotNull();
    assertThat(guardado.getUser().getId()).isEqualTo(userA.getId());

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<Professional> porUser = professionalRepository.findByUserId(userA.getId());
      assertThat(porUser).isPresent();
      assertThat(porUser.get().getId()).isEqualTo(guardado.getId());
      assertThat(porUser.get().getUser().getId()).isEqualTo(userA.getId());
      assertThat(professionalRepository.existsByUserId(userA.getId())).isTrue();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantImpideAccesoEntreClinicas() {
    Professional profA = new Professional(tenantA.getId(), "Dr. Alfa");
    profA = guardarComo(tenantA.getId(), profA);

    Professional profB = new Professional(tenantB.getId(), "Dra. Beta");
    profB = guardarComo(tenantB.getId(), profB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      // Query ingenua sólo retorna los del tenant activo
      List<Professional> visibles = professionalRepository.findAll();
      assertThat(visibles).extracting(Professional::getId).containsExactly(profA.getId());

      // Búsqueda directa por ID de otro tenant retorna Optional.empty()
      assertThat(professionalRepository.findById(profB.getId())).isEmpty();

      // Búsqueda de activos sólo del tenant activo
      List<Professional> activos = professionalRepository.findByIsActiveTrue();
      assertThat(activos).extracting(Professional::getId).containsExactly(profA.getId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void dosProfesionalesConMismoUsuarioLanzaErrorDeIntegridad() {
    Professional primero = new Professional(tenantA.getId(), "Dr. Carlos Pérez 1");
    primero.setUser(userA);
    guardarComo(tenantA.getId(), primero);

    Professional segundo = new Professional(tenantA.getId(), "Dr. Carlos Pérez 2");
    segundo.setUser(userA);

    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(() -> professionalRepository.saveAndFlush(segundo))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void claveForaneaConClinicalRecordFuncionaCorrectamente() {
    Professional odontologo = new Professional(tenantA.getId(), "Dr. Roberto Gómez");
    odontologo = guardarComo(tenantA.getId(), odontologo);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient paciente = patientRepository.save(new Patient(tenantA.getId(), "Mario", "Baracus"));
      ClinicalRecord registro = new ClinicalRecord(tenantA.getId(), paciente);
      registro.setChiefComplaint("Dolor agudo en molar inferior");
      registro.setAnamnesis("Sin antecedentes de relevancia");
      registro.setDiagnosis("Pulpitis irreversible");
      registro.setEvolution("Se indica endodoncia");
      registro.setProfessionalId(odontologo.getId());
      ClinicalRecord guardado = clinicalRecordRepository.save(registro);

      assertThat(guardado.getId()).isNotNull();
      assertThat(guardado.getProfessionalId()).isEqualTo(odontologo.getId());
    } finally {
      TenantContext.clear();
    }
  }
}

