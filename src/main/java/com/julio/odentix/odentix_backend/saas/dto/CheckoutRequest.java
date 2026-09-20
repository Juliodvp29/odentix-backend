package com.julio.odentix.odentix_backend.saas.dto;

import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud de checkout del SaaS (FASE11-04): plan + ciclo a contratar.
 * Sin Lombok.
 */
public class CheckoutRequest {

  @NotBlank(message = "planCode es obligatorio")
  private String planCode;

  private BillingCycle billingCycle;

  private String payerEmail;

  public CheckoutRequest() {
  }

  public String getPlanCode() {
    return planCode;
  }

  public void setPlanCode(String planCode) {
    this.planCode = planCode;
  }

  public BillingCycle getBillingCycle() {
    return billingCycle;
  }

  public void setBillingCycle(BillingCycle billingCycle) {
    this.billingCycle = billingCycle;
  }

  public String getPayerEmail() {
    return payerEmail;
  }

  public void setPayerEmail(String payerEmail) {
    this.payerEmail = payerEmail;
  }
}

