package com.julio.odentix.odentix_backend.saas.dto;

import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Suscripción actual del tenant (FASE11-05). Sin Lombok.
 */
public class SubscriptionResponse {

  private UUID id;
  private String planCode;
  private BillingCycle billingCycle;
  private SubscriptionStatus status;
  private Instant currentPeriodStart;
  private Instant currentPeriodEnd;
  private BigDecimal monthlyPriceCop;
  private BigDecimal annualPriceCop;

  public SubscriptionResponse() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public SubscriptionStatus getStatus() {
    return status;
  }

  public void setStatus(SubscriptionStatus status) {
    this.status = status;
  }

  public Instant getCurrentPeriodStart() {
    return currentPeriodStart;
  }

  public void setCurrentPeriodStart(Instant currentPeriodStart) {
    this.currentPeriodStart = currentPeriodStart;
  }

  public Instant getCurrentPeriodEnd() {
    return currentPeriodEnd;
  }

  public void setCurrentPeriodEnd(Instant currentPeriodEnd) {
    this.currentPeriodEnd = currentPeriodEnd;
  }

  public BigDecimal getMonthlyPriceCop() {
    return monthlyPriceCop;
  }

  public void setMonthlyPriceCop(BigDecimal monthlyPriceCop) {
    this.monthlyPriceCop = monthlyPriceCop;
  }

  public BigDecimal getAnnualPriceCop() {
    return annualPriceCop;
  }

  public void setAnnualPriceCop(BigDecimal annualPriceCop) {
    this.annualPriceCop = annualPriceCop;
  }
}

