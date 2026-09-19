package com.julio.odentix.odentix_backend.opportunity.repository;

import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
