package com.julio.odentix.odentix_backend.opportunity.dto;

import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityAction;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityActionType;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO de lectura/ejecución para una acción de oportunidad (FASE9-03).
 *
 * <p>`taskId`/`notificationId` son el resultado de la ejecución (solo
 * respuesta, sin columnas en BD). Sin Lombok (§9 AGENTS.md).
 */
public class OpportunityActionResponse {

  private UUID id;
  private UUID opportunityId;
  private OpportunityActionType actionType;
  private String channel;
  private String suggestedMessage;
  private boolean executed;
  private Instant executedAt;
  private UUID taskId;
  private UUID notificationId;

  public OpportunityActionResponse() {
  }

  public static OpportunityActionResponse fromEntity(OpportunityAction entity) {
    OpportunityActionResponse dto = new OpportunityActionResponse();
    dto.id = entity.getId();
    dto.opportunityId = entity.getOpportunity() != null ? entity.getOpportunity().getId() : null;
    dto.actionType = entity.getActionType();
    dto.channel = entity.getChannel();
    dto.suggestedMessage = entity.getSuggestedMessage();
    dto.executed = entity.isExecuted();
    dto.executedAt = entity.getExecutedAt();
    return dto;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOpportunityId() {
    return opportunityId;
  }

  public void setOpportunityId(UUID opportunityId) {
    this.opportunityId = opportunityId;
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

  public UUID getTaskId() {
    return taskId;
  }

  public void setTaskId(UUID taskId) {
    this.taskId = taskId;
  }

  public UUID getNotificationId() {
    return notificationId;
  }

  public void setNotificationId(UUID notificationId) {
    this.notificationId = notificationId;
  }
}
