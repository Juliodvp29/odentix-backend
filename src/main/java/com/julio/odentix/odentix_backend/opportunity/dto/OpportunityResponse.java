package com.julio.odentix.odentix_backend.opportunity.dto;

import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO de lectura para una oportunidad de negocio (FASE9-01).
 *
 * <p>Desacopla la API REST de la entidad interna. Sin Lombok.
 */
public class OpportunityResponse {

  private UUID id;
  private OpportunityType type;
  private String relatedEntityType;
  private UUID relatedEntityId;
  private BigDecimal estimatedValueCop;
  private short priority;
  private OpportunityStatus status;
  private Instant detectedAt;
  private Instant resolvedAt;
  private List<OpportunityActionResponse> actions = new ArrayList<>();

  public OpportunityResponse() {
  }

  /**
   * Construye el DTO a partir de la entidad.
   */
  public static OpportunityResponse fromEntity(Opportunity entity) {
    OpportunityResponse dto = new OpportunityResponse();
    dto.id = entity.getId();
    dto.type = entity.getType();
    dto.relatedEntityType = entity.getRelatedEntityType();
    dto.relatedEntityId = entity.getRelatedEntityId();
    dto.estimatedValueCop = entity.getEstimatedValueCop();
    dto.priority = entity.getPriority();
    dto.status = entity.getStatus();
    dto.detectedAt = entity.getDetectedAt();
    dto.resolvedAt = entity.getResolvedAt();
    return dto;
  }

  // --- Getters y setters ---

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public OpportunityType getType() {
    return type;
  }

  public void setType(OpportunityType type) {
    this.type = type;
  }

  public String getRelatedEntityType() {
    return relatedEntityType;
  }

  public void setRelatedEntityType(String relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
  }

  public UUID getRelatedEntityId() {
    return relatedEntityId;
  }

  public void setRelatedEntityId(UUID relatedEntityId) {
    this.relatedEntityId = relatedEntityId;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public void setEstimatedValueCop(BigDecimal estimatedValueCop) {
    this.estimatedValueCop = estimatedValueCop;
  }

  public short getPriority() {
    return priority;
  }

  public void setPriority(short priority) {
    this.priority = priority;
  }

  public OpportunityStatus getStatus() {
    return status;
  }

  public void setStatus(OpportunityStatus status) {
    this.status = status;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }

  public void setDetectedAt(Instant detectedAt) {
    this.detectedAt = detectedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  public List<OpportunityActionResponse> getActions() {
    return actions;
  }

  public void setActions(List<OpportunityActionResponse> actions) {
    this.actions = actions != null ? actions : new ArrayList<>();
  }
}

