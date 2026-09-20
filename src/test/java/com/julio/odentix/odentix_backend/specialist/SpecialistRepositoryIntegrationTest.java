package com.julio.odentix.odentix_backend.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.specialist.entity.SettlementStatus;
import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import com.julio.odentix.odentix_backend.specialist.entity.SpecialistSettlement;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistRepository;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistSettlementRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para {@link Specialist} y {@link SpecialistSettlement}
 * (FASE7-01) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Persistir un especialista sobre un profesional externo con su liquidación
 *       por periodo.</li>
 *   <li>Crear un {@code Specialist} sobre un {@code Professional} con
 *       {@code is_external = false} falla — por validación de aplicación y por
 *       el trigger de BD (ambas capas presentes, defensa en profundidad).</li>
 *   <li>Aislamiento cross-tenant: un usuario del tenant B no puede ver el
 *       especialista ni la liquidación del tenant A, incluso conociendo su UUID
 *       (regla de aislamiento multi-tenant del proyecto). La respuesta es vacía (404 a nivel HTTP), no 403.</li>
 * </ul>
 */
class SpecialistRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private SpecialistRepository specialistRepository;

  @Autowired
  private SpecialistSettlementRepository settlementRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private DataSource dataSource;

  private Tenant tenantA;
  private Tenant tenantB;
  private Professional externoA;
  private Professional plantaA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Especialistas Alfa", "930111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Especialistas Beta", "931333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      externoA = new Professional(tenantA.getId(), "Dra. Endodoncista Externa");
      externoA.setExternal(true);
      externoA = professionalRepository.saveAndFlush(externoA);

      plantaA = new Professional(tenantA.getId(), "Dr. Planta Interno");
      plantaA.setExternal(false);
      plantaA = professionalRepository.saveAndFlush(plantaA);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------------

  private Specialist guardarSpecialistComo(UUID tenantId, Professional professional,
      BigDecimal feePercentage) {
    TenantContext.setTenantId(tenantId);
    try {
      Specialist specialist = new Specialist(tenantId, professional, feePercentage);
      return specialistRepository.saveAndFlush(specialist);
    } finally {
      TenantContext.clear();
    }
  }

  private SpecialistSettlement guardarLiquidacionComo(UUID tenantId, Specialist specialist,
      LocalDate inicio, LocalDate fin) {
    TenantContext.setTenantId(tenantId);
    try {
      SpecialistSettlement settlement =
          new SpecialistSettlement(tenantId, specialist, inicio, fin);
      return settlementRepository.saveAndFlush(settlement);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE7-01)
  // ---------------------------------------------------------------------------

  @Test
  void persistirSpecialistConLiquidacionExitoso() {
    Specialist guardado = guardarSpecialistComo(
        tenantA.getId(), externoA, new BigDecimal("40.00"));

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getFeePercentage()).isEqualByComparingTo(new BigDecimal("40.00"));

    SpecialistSettlement liquidacion = guardarLiquidacionComo(
        tenantA.getId(), guardado,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    assertThat(liquidacion.getId()).isNotNull();
    // Toda liquidación nace pendiente y en ceros (el cálculo llega en FASE7-02).
    assertThat(liquidacion.getStatus()).isEqualTo(SettlementStatus.pendiente);
    assertThat(liquidacion.getGrossProductionCop()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(liquidacion.getFeeAmountCop()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(liquidacion.getPaidAt()).isNull();

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<Specialist> porProfesional =
          specialistRepository.findByProfessionalId(externoA.getId());
      assertThat(porProfesional).isPresent();
      assertThat(porProfesional.get().getId()).isEqualTo(guardado.getId());

      List<SpecialistSettlement> liquidaciones = settlementRepository
          .findBySpecialistIdOrderByPeriodStartAsc(guardado.getId());
      assertThat(liquidaciones).hasSize(1);
      assertThat(liquidaciones.get(0).getId()).isEqualTo(liquidacion.getId());
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // DoD crítico: specialist solo sobre professional externo (doble capa)
  // ---------------------------------------------------------------------------

  @Test
  void crearSpecialistSobreProfesionalNoExternoFallaEnAplicacion() {
    // Capa 1 — validación de aplicación (@PrePersist en la entidad).
    TenantContext.setTenantId(tenantA.getId());
    try {
      Specialist invalido = new Specialist(tenantA.getId(), plantaA, new BigDecimal("30.00"));
      assertThatThrownBy(() -> specialistRepository.saveAndFlush(invalido))
          .hasStackTraceContaining("is_external");
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void triggerDeBdRechazaSpecialistSobreProfesionalNoExterno() throws Exception {
    // Capa 2 — trigger trg_check_specialist_is_external, verificado con SQL
    // nativo para probar la BD aunque alguien se salte la capa JPA.
    // (RLS no bloquea: current_tenant_id() es NULL en esta conexión y la
    // política lo contempla, igual que en V11/V18.)
    String sql = "INSERT INTO specialists (tenant_id, professional_id, fee_percentage) VALUES ('"
        + tenantA.getId() + "', '" + plantaA.getId() + "', 30.00)";
    try (var connection = dataSource.getConnection();
        var stmt = connection.createStatement()) {
      assertThatThrownBy(() -> stmt.executeUpdate(sql))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("is_external");
    }

    // El trigger sí deja pasar a un profesional externo.
    String sqlValido = "INSERT INTO specialists (tenant_id, professional_id, fee_percentage)"
        + " VALUES ('" + tenantA.getId() + "', '" + externoA.getId() + "', 35.00)";
    try (var connection = dataSource.getConnection();
        var stmt = connection.createStatement()) {
      assertThat(stmt.executeUpdate(sqlValido)).isEqualTo(1);
    }
  }

  @Test
  void feePercentageFueraDeRangoFalla() throws Exception {
    // Validación de aplicación en el setter.
    Specialist specialist = new Specialist();
    assertThatThrownBy(() -> specialist.setFeePercentage(new BigDecimal("150.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("entre 0 y 100");

    // CHECK de BD como red de seguridad (SQL nativo, salta el setter).
    // Se usa un profesional externo fresco del setUp de este test.
    TenantContext.setTenantId(tenantA.getId());
    Professional otroExterno;
    try {
      otroExterno = new Professional(tenantA.getId(), "Dr. Externo Rango");
      otroExterno.setExternal(true);
      otroExterno = professionalRepository.saveAndFlush(otroExterno);
    } finally {
      TenantContext.clear();
    }
    String sql = "INSERT INTO specialists (tenant_id, professional_id, fee_percentage) VALUES ('"
        + tenantA.getId() + "', '" + otroExterno.getId() + "', 150.00)";
    try (var connection = dataSource.getConnection();
        var stmt = connection.createStatement()) {
      assertThatThrownBy(() -> stmt.executeUpdate(sql))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("fee_percentage");
    }
  }

  @Test
  void segundoSpecialistSobreMismoProfessionalFallaPorUnique() {
    guardarSpecialistComo(tenantA.getId(), externoA, new BigDecimal("40.00"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      Specialist duplicado = new Specialist(tenantA.getId(), externoA, new BigDecimal("50.00"));
      assertThatThrownBy(() -> specialistRepository.saveAndFlush(duplicado))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void liquidacionConPeriodoInvertidoFalla() throws Exception {
    Specialist specialist =
        guardarSpecialistComo(tenantA.getId(), externoA, new BigDecimal("40.00"));

    // Validación de aplicación (@PrePersist en la entidad).
    TenantContext.setTenantId(tenantA.getId());
    try {
      SpecialistSettlement invertida = new SpecialistSettlement(
          tenantA.getId(), specialist,
          LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1));
      assertThatThrownBy(() -> settlementRepository.saveAndFlush(invertida))
          .hasStackTraceContaining("period_end");
    } finally {
      TenantContext.clear();
    }

    // CHECK de BD como red de seguridad (PG nombra el constraint
    // "specialist_settlements_check", sin la columna en el mensaje).
    String sql = "INSERT INTO specialist_settlements (tenant_id, specialist_id,"
        + " period_start, period_end) VALUES ('"
        + tenantA.getId() + "', '" + specialist.getId() + "', '2026-09-30', '2026-09-01')";
    try (var connection = dataSource.getConnection();
        var stmt = connection.createStatement()) {
      assertThatThrownBy(() -> stmt.executeUpdate(sql))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("check constraint");
    }
  }

  // ---------------------------------------------------------------------------
  // Test de aislamiento cross-tenant (regla de aislamiento multi-tenant del proyecto — obligatorio)
  // ---------------------------------------------------------------------------

  @Test
  void tenantBNoPuedeVerSpecialistNiLiquidacionDelTenantA() {
    Specialist specialistA =
        guardarSpecialistComo(tenantA.getId(), externoA, new BigDecimal("40.00"));
    SpecialistSettlement liquidacionA = guardarLiquidacionComo(
        tenantA.getId(), specialistA,
        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
    UUID idSpecialistA = specialistA.getId();
    UUID idLiquidacionA = liquidacionA.getId();

    // Autenticado como tenantB, los UUID de tenantA son invisibles (filtro @TenantId).
    TenantContext.setTenantId(tenantB.getId());
    try {
      // Búsqueda directa por ID → vacío, no 403 (no confirma que existe).
      Optional<Specialist> specialist = specialistRepository.findById(idSpecialistA);
      assertThat(specialist).isEmpty();

      Optional<SpecialistSettlement> liquidacion =
          settlementRepository.findById(idLiquidacionA);
      assertThat(liquidacion).isEmpty();

      // Query ingenua → solo lo propio (nada, tenantB no tiene especialistas).
      assertThat(specialistRepository.findAll()).isEmpty();
      assertThat(settlementRepository.findAll()).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }
}

