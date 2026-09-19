package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityActionResponse;
import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityResponse;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityActionRepository;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de consulta de oportunidades para la API REST (FASE9-01).
 *
 * <p>El tenant siempre sale del {@code TenantContext}: oportunidades de otro
 * tenant resultan invisibles, conforme a la regla §5 de AGENTS.md.
 */
@Service
public class OpportunityService {

  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionRepository actionRepository;

  public OpportunityService(
      OpportunityRepository opportunityRepository,
      OpportunityActionRepository actionRepository) {
    this.opportunityRepository = opportunityRepository;
    this.actionRepository = actionRepository;
  }

  /**
   * Lista las oportunidades del tenant activo, filtradas opcionalmente por estado.
   *
   * @param status estado a filtrar, o {@code null} para listar todas.
   * @return lista de oportunidades ordenadas por prioridad DESC y fecha de detección DESC.
   */
  @Transactional(readOnly = true)
  public List<OpportunityResponse> listarOportunidades(OpportunityStatus status) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    if (status != null) {
      return opportunityRepository
          .findByTenantIdAndStatusOrderByPriorityDescDetectedAtDesc(tenantId, status)
          .stream()
          .map(this::aResponse)
          .toList();
    }

    return opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantId)
        .stream()
        .map(this::aResponse)
        .toList();
  }

  private OpportunityResponse aResponse(Opportunity oportunidad) {
    OpportunityResponse response = OpportunityResponse.fromEntity(oportunidad);
    response.setActions(actionRepository.findByOpportunityId(oportunidad.getId()).stream()
        .map(OpportunityActionResponse::fromEntity)
        .toList());
    return response;
  }
}
