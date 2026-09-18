package com.julio.odentix.odentix_backend.treatmentplan.dto;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación pública de un procedimiento o ítem del plan de tratamiento (FASE4-02).
 */
public class TreatmentPlanItemResponse {

  private UUID id;
  private UUID procedureId;
  private Short toothNumber;
  private BigDecimal priceCop;
  private BigDecimal discountCop;
  private BigDecimal netPriceCop;
  private Instant createdAt;

  public TreatmentPlanItemResponse() {
  }

  public TreatmentPlanItemResponse(
      UUID id,
      UUID procedureId,
      Short toothNumber,
      BigDecimal priceCop,
      BigDecimal discountCop,
      BigDecimal netPriceCop,
      Instant createdAt) {
    this.id = id;
    this.procedureId = procedureId;
    this.toothNumber = toothNumber;
    this.priceCop = priceCop;
    this.discountCop = discountCop;
    this.netPriceCop = netPriceCop;
    this.createdAt = createdAt;
  }

  public static TreatmentPlanItemResponse fromEntity(TreatmentPlanItem item) {
    if (item == null) {
      return null;
    }
    return new TreatmentPlanItemResponse(
        item.getId(),
        item.getProcedureId(),
        item.getToothNumber(),
        item.getPriceCop(),
        item.getDiscountCop(),
        item.getNetPrice(),
        item.getCreatedAt()
    );
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Short getToothNumber() {
    return toothNumber;
  }

  public void setToothNumber(Short toothNumber) {
    this.toothNumber = toothNumber;
  }

  public BigDecimal getPriceCop() {
    return priceCop;
  }

  public void setPriceCop(BigDecimal priceCop) {
    this.priceCop = priceCop;
  }

  public BigDecimal getDiscountCop() {
    return discountCop;
  }

  public void setDiscountCop(BigDecimal discountCop) {
    this.discountCop = discountCop;
  }

  public BigDecimal getNetPriceCop() {
    return netPriceCop;
  }

  public void setNetPriceCop(BigDecimal netPriceCop) {
    this.netPriceCop = netPriceCop;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
