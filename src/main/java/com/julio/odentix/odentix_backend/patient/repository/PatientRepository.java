package com.julio.odentix.odentix_backend.patient.repository;

import com.julio.odentix.odentix_backend.patient.entity.Patient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para Patient (FASE2-01 / FASE2-03).
 *
 * <p>El filtro automático @TenantId (heredado de TenantAwareEntity, FASE1-09)
 * ya aísla por tenant, pero cada método filtra además por {@code tenantId}
 * explícito: defensa en profundidad (filtro explícito por tenant). Nunca agregar
 * aquí una query JPQL/nativa sin filtrar por {@code tenant_id}.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

  List<Patient> findAllByTenantId(UUID tenantId);

  Optional<Patient> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Conteo de pacientes activos (insumo del límite `max_patients` de FASE11-03:
   * el plan limita pacientes activos, no filas históricas).
   */
  long countByTenantIdAndIsActiveTrue(UUID tenantId);

  Page<Patient> findAllByTenantIdAndIsActiveTrue(UUID tenantId, Pageable pageable);

  @Query("""
      SELECT p FROM Patient p
      WHERE p.tenantId = :tenantId
        AND p.isActive = true
        AND (
          cast(function('immutable_unaccent', lower(concat(p.firstName, ' ', p.lastName))) as string) LIKE concat('%', cast(function('immutable_unaccent', lower(:query)) as string), '%')
          OR cast(function('immutable_unaccent', lower(concat(p.lastName, ' ', p.firstName))) as string) LIKE concat('%', cast(function('immutable_unaccent', lower(:query)) as string), '%')
          OR (p.documentNumber IS NOT NULL AND lower(p.documentNumber) LIKE concat('%', lower(:query), '%'))
        )
      """)
  Page<Patient> search(
      @Param("tenantId") UUID tenantId,
      @Param("query") String query,
      Pageable pageable);

  /**
   * Pacientes candidatos a `paciente_inactivo` (FASE9-02): activos sin citas
   * ni planes de tratamiento desde el corte. En contexto de sistema cubre
   * todos los tenants; cada oportunidad hereda el tenant del paciente.
   */
  @Query("""
      SELECT p FROM Patient p
      WHERE p.isActive = true
        AND NOT EXISTS (
          SELECT 1 FROM Appointment a WHERE a.patient = p AND a.startsAt >= :cutoff)
        AND NOT EXISTS (
          SELECT 1 FROM TreatmentPlan t WHERE t.patient = p AND t.createdAt >= :cutoff)
      """)
  List<Patient> findInactiveSince(@Param("cutoff") Instant cutoff);
}


