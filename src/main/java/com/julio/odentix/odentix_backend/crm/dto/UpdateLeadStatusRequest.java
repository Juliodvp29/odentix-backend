package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud de cambio de estado en el pipeline comercial del prospecto (FASE5-02).
 */
public class UpdateLeadStatusRequest {

  @NotNull(message = "El nuevo estado es obligatorio")
  private LeadStatus status;

  private String notes;

  public UpdateLeadStatusRequest() {
  }

  public UpdateLeadStatusRequest(LeadStatus status) {
    this.status = status;
  }

  public UpdateLeadStatusRequest(LeadStatus status, String notes) {
    this.status = status;
    this.notes = notes;
  }

  public LeadStatus getStatus() {
    return status;
  }

  public void setStatus(LeadStatus status) {
    this.status = status;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}
