package com.julio.odentix.odentix_backend.opportunity.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Acción sugerida para una oportunidad (FASE9-03: "proponer").
 *
 * <p>Cada regla de detección genera al menos una acción junto a la oportunidad;
 * la acción se ejecuta vía
 * {@code POST /api/v1/opportunities/{id}/actions/{actionId}/execute}:
 * {@code crear_tarea} crea una {@code Task} y {@code enviar_mensaje} envía por
 * el canal indicado. Una acción ejecutada no se puede re-ejecutar (409).
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "opportunity_actions")
public class OpportunityAction extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "opportunity_id", nullable = false)
  private Opportunity opportunity;

  // TEXT en BD (fiel a schema.sql): @Enumerated(STRING) plano, sin
  // PostgreSQLEnumJdbcType (ese solo aplica a tipos enum reales de PG).
  @Enumerated(EnumType.STRING)
  @Column(name = "action_type", nullable = false, columnDefinition = "TEXT")
  private OpportunityActionType actionType;

  /**
   * Canal para `enviar_mensaje` (email/WhatsApp/sms). NULL en `crear_tarea`.
   * Se guarda como TEXT (no hay enum PG en `schema.sql` para esto).
   */
  @Column(name = "channel")
  private String channel;

  @Column(name = "suggested_message", columnDefinition = "TEXT")
  private String suggestedMessage;

  @Column(name = "executed", nullable = false)
  private boolean executed = false;

  @Column(name = "executed_at")
  private Instant executedAt;

  @Column(name = "executed_by")
  private UUID executedBy;

  public OpportunityAction() {
    super();
  }

  public OpportunityAction(UUID tenantId, Opportunity opportunity, OpportunityActionType actionType) {
    super(tenantId);
    this.opportunity = opportunity;
    this.actionType = actionType;
  }

  public Opportunity getOpportunity() {
    return opportunity;
  }

  public void setOpportunity(Opportunity opportunity) {
    this.opportunity = opportunity;
  }

  public OpportunityActionType getActionType() {
    return actionType;
  }

  public void setActionType(OpportunityActionType actionType) {
    this.actionType = actionType;
  }

  public String getChannel() {
    return channel;
  }

  public void setChannel(String channel) {
    this.channel = channel;
  }

  public String getSuggestedMessage() {
    return suggestedMessage;
  }

  public void setSuggestedMessage(String suggestedMessage) {
    this.suggestedMessage = suggestedMessage;
  }

  public boolean isExecuted() {
    return executed;
  }

  public void setExecuted(boolean executed) {
    this.executed = executed;
  }

  public Instant getExecutedAt() {
    return executedAt;
  }

  public void setExecutedAt(Instant executedAt) {
    this.executedAt = executedAt;
  }

  public UUID getExecutedBy() {
    return executedBy;
  }

  public void setExecutedBy(UUID executedBy) {
    this.executedBy = executedBy;
  }

  @Override
  public String toString() {
    return "OpportunityAction{"
        + "id=" + getId()
        + ", actionType=" + actionType
        + ", executed=" + executed
        + '}';
  }
}
