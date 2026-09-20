package com.julio.odentix.odentix_backend.opportunity.dto;

import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import java.math.BigDecimal;

/**
 * Valor recuperado por categoría de oportunidad en un periodo (FASE9-04).
 *
 * <p>Sin Lombok.
 */
public class RecoveredValueResponse {

  private OpportunityType type;
  private BigDecimal totalAmountCop;
  private long count;

  public RecoveredValueResponse() {
  }

  public RecoveredValueResponse(OpportunityType type, BigDecimal totalAmountCop, long count) {
    this.type = type;
    this.totalAmountCop = totalAmountCop;
    this.count = count;
  }

  public OpportunityType getType() {
    return type;
  }

  public void setType(OpportunityType type) {
    this.type = type;
  }

  public BigDecimal getTotalAmountCop() {
    return totalAmountCop;
  }

  public void setTotalAmountCop(BigDecimal totalAmountCop) {
    this.totalAmountCop = totalAmountCop;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }
}

