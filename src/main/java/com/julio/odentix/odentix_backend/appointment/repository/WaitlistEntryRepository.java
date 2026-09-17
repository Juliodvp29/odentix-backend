package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link WaitlistEntry} (FASE3-06).
 *
 * <p>Mínimo para este ticket (registro); las queries de compatibilidad de
 * candidatos llegan en FASE3-07.
 */
@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

  /**
   * Busca una entrada por ID dentro del tenant activo.
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<WaitlistEntry> findByIdAndTenantId(UUID id, UUID tenantId);
}
