package com.julio.odentix.odentix_backend.patient.repository;

import com.julio.odentix.odentix_backend.patient.entity.Patient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para Patient (FASE2-01).
 *
 * <p>El filtro automático @TenantId (heredado de TenantAwareEntity, FASE1-09)
 * ya aísla por tenant, pero cada método filtra además por {@code tenantId}
 * explícito: defensa en profundidad (regla §5.2 de AGENTS.md). Nunca agregar
 * aquí una query JPQL/nativa sin filtrar por {@code tenant_id}.
 */
@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

  List<Patient> findAllByTenantId(UUID tenantId);

  Optional<Patient> findByIdAndTenantId(UUID id, UUID tenantId);
}
