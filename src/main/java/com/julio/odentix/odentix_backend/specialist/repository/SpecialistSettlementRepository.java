package com.julio.odentix.odentix_backend.specialist.repository;

import com.julio.odentix.odentix_backend.specialist.entity.SpecialistSettlement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link SpecialistSettlement} (FASE7-01).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries. No es necesario agregar {@code AND tenant_id = ?} manualmente.
 */
@Repository
public interface SpecialistSettlementRepository extends JpaRepository<SpecialistSettlement, UUID> {

  /**
   * Busca una liquidación por su ID y tenant (defensa en profundidad por tenant).
   */
  Optional<SpecialistSettlement> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Liquidaciones de un especialista ordenadas por inicio de periodo.
   *
   * @param specialistId identificador del especialista.
   * @return liquidaciones del especialista en el tenant activo.
   */
  List<SpecialistSettlement> findBySpecialistIdOrderByPeriodStartAsc(UUID specialistId);

  /**
   * Indica si ya existe una liquidación para el especialista con el mismo inicio
   * de periodo (política de idempotencia de FASE7-02: un periodo se liquida una vez).
   */
  boolean existsBySpecialistIdAndPeriodStart(UUID specialistId, java.time.LocalDate periodStart);
}

