package com.julio.odentix.odentix_backend.opportunity.repository;

import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityAction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link OpportunityAction} (FASE9-03).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
@Repository
public interface OpportunityActionRepository extends JpaRepository<OpportunityAction, UUID> {

  /**
   * Busca una acción por su ID y tenant (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<OpportunityAction> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Acciones sugeridas de una oportunidad (las que trae asociada la bandeja).
   */
  List<OpportunityAction> findByOpportunityId(UUID opportunityId);
}
