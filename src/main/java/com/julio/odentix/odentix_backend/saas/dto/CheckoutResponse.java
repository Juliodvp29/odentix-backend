package com.julio.odentix.odentix_backend.saas.dto;

import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Respuesta del checkout: URL de pago de Bold y suscripción (FASE11-04).
 * Sin Lombok.
 */
public class CheckoutResponse {

  private UUID subscriptionId;
  private String planCode;
  private BillingCycle billingCycle;
  private BigDecimal amountCop;
  private String paymentUrl;
  private SubscriptionStatus status;

  public CheckoutResponse() {
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public void setSubscriptionId(UUID subscriptionId) {
    this.subscriptionId = subscriptionId;
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

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public void setAmountCop(BigDecimal amountCop) {
    this.amountCop = amountCop;
  }

  public String getPaymentUrl() {
    return paymentUrl;
  }

  public void setPaymentUrl(String paymentUrl) {
    this.paymentUrl = paymentUrl;
  }

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }
}

