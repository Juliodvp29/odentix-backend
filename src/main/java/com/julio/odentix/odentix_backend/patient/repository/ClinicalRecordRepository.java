package com.julio.odentix.odentix_backend.patient.repository;

import com.julio.odentix.odentix_backend.patient.entity.ClinicalRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para ClinicalRecord (FASE2-05).
 *
 * <p>El filtro automático @TenantId (heredado de TenantAwareEntity, FASE1-09)
 * ya aísla por tenant, pero cada método filtra además por {@code tenantId}
 * explícito: defensa en profundidad (filtro explícito por tenant). Nunca agregar
 * aquí una query JPQL/nativa sin filtrar por {@code tenant_id}.
 */
@Repository
public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, UUID> {

  /**
   * Registros clínicos de un paciente, ordenados del más reciente al más
   * antiguo (por {@code recordedAt} descendente).
   */
  List<ClinicalRecord> findAllByTenantIdAndPatientIdOrderByRecordedAtDesc(
      UUID tenantId, UUID patientId);

  /**
   * Buscar un registro clínico por ID, filtrado por tenant (defensa en
   * profundidad: evita acceso cross-tenant aunque @TenantId ya lo filtre).
   */
  Optional<ClinicalRecord> findByIdAndTenantId(UUID id, UUID tenantId);
}

