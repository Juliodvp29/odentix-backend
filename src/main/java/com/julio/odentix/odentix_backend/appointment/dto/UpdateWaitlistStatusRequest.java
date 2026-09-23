package com.julio.odentix.odentix_backend.appointment.dto;

import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import jakarta.validation.constraints.NotNull;

public class UpdateWaitlistStatusRequest {

  @NotNull(message = "El estado destino es obligatorio")
  private WaitlistStatus status;

  private String discardReason;

  public UpdateWaitlistStatusRequest() {
  }

  public UpdateWaitlistStatusRequest(WaitlistStatus status) {
    this.status = status;
  }

  public WaitlistStatus getStatus() {
    return status;
  }

  public void setStatus(WaitlistStatus status) {
    this.status = status;
  }

  public String getDiscardReason() {
    return discardReason;
  }

  public void setDiscardReason(String discardReason) {
    this.discardReason = discardReason;
  }
}
