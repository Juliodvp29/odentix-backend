package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.crm.entity.LeadActivity;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivityType;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación pública de una actividad de seguimiento con un prospecto (FASE5-02).
 */
public class LeadActivityResponse {

  private UUID id;
  private UUID tenantId;
  private UUID leadId;
  private UUID userId;
  private String userName;
  private LeadActivityType activityType;
  private String notes;
  private Instant createdAt;

  public LeadActivityResponse() {
  }

  public LeadActivityResponse(
      UUID id,
      UUID tenantId,
      UUID leadId,
      UUID userId,
      String userName,
      LeadActivityType activityType,
      String notes,
      Instant createdAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.leadId = leadId;
    this.userId = userId;
    this.userName = userName;
    this.activityType = activityType;
    this.notes = notes;
    this.createdAt = createdAt;
  }

  public static LeadActivityResponse fromEntity(LeadActivity activity) {
    if (activity == null) {
      return null;
    }
    return new LeadActivityResponse(
        activity.getId(),
        activity.getTenantId(),
        activity.getLead() != null ? activity.getLead().getId() : null,
        activity.getUser() != null ? activity.getUser().getId() : null,
        activity.getUser() != null ? activity.getUser().getFullName() : null,
        activity.getActivityType(),
        activity.getNotes(),
        activity.getCreatedAt()
    );
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public void setLeadId(UUID leadId) {
    this.leadId = leadId;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public LeadActivityType getActivityType() {
    return activityType;
  }

  public void setActivityType(LeadActivityType activityType) {
    this.activityType = activityType;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
