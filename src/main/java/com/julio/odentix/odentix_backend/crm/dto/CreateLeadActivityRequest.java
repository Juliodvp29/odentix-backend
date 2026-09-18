package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.crm.entity.LeadActivityType;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de registro de una actividad de seguimiento con un prospecto (FASE5-02).
 */
public class CreateLeadActivityRequest {

  @NotNull(message = "El tipo de actividad es obligatorio")
  private LeadActivityType activityType;

  private String notes;

  public CreateLeadActivityRequest() {
  }

  public CreateLeadActivityRequest(LeadActivityType activityType, String notes) {
    this.activityType = activityType;
    this.notes = notes;
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
}
