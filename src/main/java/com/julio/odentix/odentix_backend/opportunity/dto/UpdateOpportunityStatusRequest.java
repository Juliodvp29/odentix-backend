package com.julio.odentix.odentix_backend.opportunity.dto;

import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para cambiar el estado de una oportunidad (FASE9-04).
 *
 * <p>Sin Lombok (§9 AGENTS.md).
 */
public class UpdateOpportunityStatusRequest {

  @NotNull(message = "status es obligatorio")
  private OpportunityStatus status;

  public UpdateOpportunityStatusRequest() {
  }

  public UpdateOpportunityStatusRequest(OpportunityStatus status) {
    this.status = status;
  }

  public OpportunityStatus getStatus() {
    return status;
  }

  public void setStatus(OpportunityStatus status) {
    this.status = status;
  }
}
