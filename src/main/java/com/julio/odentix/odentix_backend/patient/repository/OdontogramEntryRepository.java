package com.julio.odentix.odentix_backend.patient.repository;

import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para OdontogramEntry (FASE2-07).
 *
 * <p>El filtro automático @TenantId (heredado de TenantAwareEntity, FASE1-09)
 * ya aísla por tenant, pero cada método filtra además por {@code tenantId}
 * explícito: defensa en profundidad (regla §5.2 de AGENTS.md). Nunca agregar
 * aquí una query JPQL/nativa sin filtrar por {@code tenant_id}.
 *
 * <p>El listado agrupado por pieza y tipo para el GET de FASE2-08 se apoyará
 * en {@link #findAllByTenantIdAndPatientId(UUID, UUID)}.
 */
@Repository
public interface OdontogramEntryRepository extends JpaRepository<OdontogramEntry, UUID> {

  List<OdontogramEntry> findAllByTenantId(UUID tenantId);

  List<OdontogramEntry> findAllByTenantIdAndPatientId(UUID tenantId, UUID patientId);

  Optional<OdontogramEntry> findByIdAndTenantId(UUID id, UUID tenantId);
}
