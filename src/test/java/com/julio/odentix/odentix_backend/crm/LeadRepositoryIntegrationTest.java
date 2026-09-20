package com.julio.odentix.odentix_backend.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivity;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivityType;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadActivityRepository;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaSystemException;

/**
 * Pruebas de integración para {@link Lead} y {@link LeadActivity} (FASE5-01)
 * contra PostgreSQL 16 real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Persistir un lead con campos comerciales y estado inicial {@code nuevo}.</li>
 *   <li>Registrar y consultar historial de actividades de contacto asociadas al lead.</li>
 *   <li>Garantizar consistencia multi-tenant rechazando usuarios asignados de otro tenant.</li>
 *   <li>Garantizar consistencia multi-tenant rechazando pacientes convertidos de otro tenant.</li>
 *   <li>Aislamiento cross-tenant estricto (regla de aislamiento multi-tenant del proyecto).</li>
 * </ul>
 */
class LeadRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private LeadRepository leadRepository;

  @Autowired
  private LeadActivityRepository leadActivityRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private PatientRepository patientRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private User userA;
  private User userB;
  private Patient patientA;
  private Patient patientB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Estética Dental Alfa", "901234567-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Estética Dental Beta", "902345678-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      userA = userRepository.save(new User(tenantA, "recepcion.alfa@odentix.test", "hash123", "Recepción Alfa", UserRole.recepcion));
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Carlos", "Restrepo"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      userB = userRepository.save(new User(tenantB, "recepcion.beta@odentix.test", "hash456", "Recepción Beta", UserRole.recepcion));
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Felipe", "Gómez"));
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void crearLeadConPipelineInicialExitoso() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Lead lead = new Lead(tenantA.getId(), "Ana Milena Pérez", "+573001234567", "ana.perez@example.com", "instagram");
      lead.setCampaign("ortodoncia_invisible_marzo");
      lead.setProcedureOfInterest("Alineadores invisibles");
      lead.setEstimatedValueCop(new BigDecimal("3500000.00"));
      lead.setAssignedTo(userA);
      lead.setNextActionAt(Instant.now().plusSeconds(86400));

      Lead saved = leadRepository.saveAndFlush(lead);

      assertThat(saved.getId()).isNotNull();
      assertThat(saved.getTenantId()).isEqualTo(tenantA.getId());
      assertThat(saved.getStatus()).isEqualTo(LeadStatus.nuevo);
      assertThat(saved.getFullName()).isEqualTo("Ana Milena Pérez");
      assertThat(saved.getEstimatedValueCop()).isEqualByComparingTo("3500000.00");
      assertThat(saved.getAssignedTo().getId()).isEqualTo(userA.getId());
      assertThat(saved.getCreatedAt()).isNotNull();
      assertThat(saved.getUpdatedAt()).isNotNull();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void registrarActividadesDeContactoEnLead() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Lead lead = new Lead(tenantA.getId(), "Roberto Gómez", "+573109876543", "roberto@example.com", "meta_ads");
      Lead savedLead = leadRepository.saveAndFlush(lead);

      LeadActivity actividad1 = new LeadActivity(tenantA.getId(), savedLead, userA, LeadActivityType.llamada, "Llamada inicial: no contestó, buzón");
      LeadActivity actividad2 = new LeadActivity(tenantA.getId(), savedLead, userA, LeadActivityType.whatsapp, "Mensaje enviado por WhatsApp con información del tratamiento");

      leadActivityRepository.saveAndFlush(actividad1);
      leadActivityRepository.saveAndFlush(actividad2);

      List<LeadActivity> actividades = leadActivityRepository.findByLeadIdOrderByCreatedAtDesc(savedLead.getId());

      assertThat(actividades).hasSize(2);
      assertThat(actividades)
          .extracting(LeadActivity::getActivityType)
          .containsExactlyInAnyOrder(LeadActivityType.llamada, LeadActivityType.whatsapp);
      assertThat(actividades.get(0).getLead().getId()).isEqualTo(savedLead.getId());
      assertThat(actividades.get(0).getTenantId()).isEqualTo(tenantA.getId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void impedirAsignarUsuarioDeOtroTenant() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Lead lead = new Lead(tenantA.getId(), "Camilo Torres", "+573204567890", "camilo@example.com", "google_ads");
      // userB pertenece a tenantB, violando la regla de aislamiento
      lead.setAssignedTo(userB);

      assertThatThrownBy(() -> leadRepository.saveAndFlush(lead))
          .isInstanceOf(JpaSystemException.class)
          .hasMessageContaining("no pertenece al tenant");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void impedirVincularPacienteDeOtroTenant() {
    TenantContext.setTenantId(tenantA.getId());
    try {
      Lead lead = new Lead(tenantA.getId(), "Diana Morales", "+573157894561", "diana@example.com", "referido");
      // patientB pertenece a tenantB
      lead.setConvertedPatient(patientB);

      assertThatThrownBy(() -> leadRepository.saveAndFlush(lead))
          .isInstanceOf(JpaSystemException.class)
          .hasMessageContaining("no pertenece al tenant");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantEstricto() {
    Lead leadTenantA;
    TenantContext.setTenantId(tenantA.getId());
    try {
      leadTenantA = leadRepository.saveAndFlush(
          new Lead(tenantA.getId(), "Lead Confidencial Alfa", "+573005555555", "alfa@leads.test", "meta_ads"));
      leadActivityRepository.saveAndFlush(
          new LeadActivity(tenantA.getId(), leadTenantA, userA, LeadActivityType.nota, "Nota privada interna Alfa"));
    } finally {
      TenantContext.clear();
    }

    // Cambiar al contexto de Tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      // 1. findById directo sobre lead de tenant A
      Optional<Lead> leadEncontrado = leadRepository.findById(leadTenantA.getId());
      assertThat(leadEncontrado).isEmpty();

      // 2. findAll no incluye leads de tenant A
      List<Lead> todosLosLeads = leadRepository.findAll();
      assertThat(todosLosLeads).noneMatch(l -> l.getId().equals(leadTenantA.getId()));

      // 3. Actividades del lead no son accesibles desde tenant B
      List<LeadActivity> actividades = leadActivityRepository.findByLeadIdOrderByCreatedAtDesc(leadTenantA.getId());
      assertThat(actividades).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}

