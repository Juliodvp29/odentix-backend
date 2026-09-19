package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityActionResponse;
import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityResponse;
import com.julio.odentix.odentix_backend.opportunity.dto.RecoveredValueResponse;
import com.julio.odentix.odentix_backend.opportunity.dto.UpdateOpportunityStatusRequest;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityActionRepository;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

  /**
   * Cambia el estado de una oportunidad (FASE9-04).
   *
   * <p>Al resolver fija `resolvedAt = now()`; al salir de `resuelta` lo limpia.
   * Sin esta transición nada podría llegar a `resuelta` y la métrica de valor
   * recuperado siempre daría cero.
   */
  @Transactional
  public OpportunityResponse actualizarEstado(UUID id, UpdateOpportunityStatusRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Opportunity oportunidad = opportunityRepository.findById(id)
        .filter(o -> tenantId.equals(o.getTenantId()))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Oportunidad no encontrada: " + id));

    oportunidad.setStatus(request.getStatus());
    if (request.getStatus() == OpportunityStatus.resuelta) {
      oportunidad.setResolvedAt(Instant.now());
    } else {
      oportunidad.setResolvedAt(null);
    }
    return aResponse(opportunityRepository.save(oportunidad));
  }

  /**
   * Valor recuperado por categoría en un periodo (FASE9-04, sección 10 del doc
   * de arquitectura).
   *
   * <p><b>Criterio de atribución (v1):</b> una oportunidad cuenta como recuperada
   * si está `resuelta`, su `resolvedAt` cae en el periodo y tiene al menos una
   * acción ejecutada con `executedAt <= resolvedAt` (orden causal: primero se
   * actuó, después se resolvió). Una resolución sin acción previa es "orgánica"
   * y no atribuye valor al motor. El monto atribuido es el `estimatedValueCop`
   * de la oportunidad.
   *
   * <p><b>Limitación conocida:</b> `resuelta` la marca un humano; la resolución
   * automática al ocurrir el evento de negocio (pago cobrado, cita reagendada…)
   * queda como mejora futura.
   *
   * @param from inicio del periodo (inclusivo).
   * @param to fin del periodo (exclusivo).
   * @return una fila por categoría con oportunidades recuperadas (vacío si ninguna).
   */
  @Transactional(readOnly = true)
  public List<RecoveredValueResponse> valorRecuperado(Instant from, Instant to) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("El inicio del periodo debe ser anterior al fin.");
    }

    List<Opportunity> recuperadas = opportunityRepository.findRecoveredInPeriod(
        tenantId, OpportunityStatus.resuelta, from, to);

    Map<OpportunityType, BigDecimal> montos = new EnumMap<>(OpportunityType.class);
    Map<OpportunityType, Long> conteos = new EnumMap<>(OpportunityType.class);
    for (Opportunity oportunidad : recuperadas) {
      montos.merge(oportunidad.getType(),
          oportunidad.getEstimatedValueCop() != null
              ? oportunidad.getEstimatedValueCop()
              : BigDecimal.ZERO,
          BigDecimal::add);
      conteos.merge(oportunidad.getType(), 1L, Long::sum);
    }

    return montos.entrySet().stream()
        .map(entry -> new RecoveredValueResponse(
            entry.getKey(), entry.getValue(), conteos.get(entry.getKey())))
        .sorted((a, b) -> b.getTotalAmountCop().compareTo(a.getTotalAmountCop()))
        .toList();
  }
}
