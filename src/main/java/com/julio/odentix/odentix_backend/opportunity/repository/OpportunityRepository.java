package com.julio.odentix.odentix_backend.opportunity.repository;

import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Opportunity} (FASE9-01).
 *
 * <p>El filtro automático de tenant ({@code @TenantId} de Hibernate) se aplica en
 * todas las queries derivadas. Los métodos que filtran por {@code tenantId} lo hacen
 * por defensa en profundidad (regla §5.2 de AGENTS.md).
 */
@Repository
public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {

  /**
   * Verifica si ya existe una oportunidad abierta para la misma entidad relacionada.
   *
   * <p>Insumo de la idempotencia: el job de detección consulta esto antes de crear
   * una nueva oportunidad, evitando duplicados en ejecuciones repetidas.
   *
   * @param relatedEntityType tipo de la entidad (ej. "treatment_plan").
   * @param relatedEntityId   id de la entidad.
   * @param statuses          estados considerados "abiertos" (abierta, en_progreso).
   * @return {@code true} si ya existe al menos una oportunidad activa.
   */
  boolean existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
      String relatedEntityType, UUID relatedEntityId, Collection<OpportunityStatus> statuses);

  /**
   * Lista oportunidades del tenant activo filtradas por estado, ordenadas por
   * prioridad descendente y fecha de detección descendente.
   */
  List<Opportunity> findByTenantIdAndStatusOrderByPriorityDescDetectedAtDesc(
      UUID tenantId, OpportunityStatus status);

  /**
   * Lista todas las oportunidades del tenant activo, ordenadas por prioridad
   * descendente y fecha de detección descendente.
   */
  List<Opportunity> findAllByTenantIdOrderByPriorityDescDetectedAtDesc(UUID tenantId);

  /**
   * Oportunidades recuperadas en un periodo (FASE9-04): `resuelta` con
   * `resolvedAt` en el rango y al menos una acción ejecutada antes o al
   * resolver (orden causal). Con filtro explícito de `tenantId` además del
   * automático @TenantId (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  @Query("""
      SELECT o FROM Opportunity o
      WHERE o.tenantId = :tenantId
        AND o.status = :status
        AND o.resolvedAt >= :from
        AND o.resolvedAt < :to
        AND EXISTS (
          SELECT 1 FROM OpportunityAction a
          WHERE a.opportunity = o
            AND a.executed = true
            AND a.executedAt <= o.resolvedAt)
      """)
  List<Opportunity> findRecoveredInPeriod(
      @Param("tenantId") UUID tenantId,
      @Param("status") OpportunityStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to);
}
