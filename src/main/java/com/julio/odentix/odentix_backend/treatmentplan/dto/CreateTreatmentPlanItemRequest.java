package com.julio.odentix.odentix_backend.treatmentplan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solicitud de creación de un ítem o procedimiento dentro de un plan de tratamiento (FASE4-02).
 */
public class CreateTreatmentPlanItemRequest {

  private UUID procedureId;

  @Min(value = 11, message = "El número de pieza dental no puede ser menor a 11 (notación FDI)")
  @Max(value = 48, message = "El número de pieza dental no puede ser mayor a 48 (notación FDI)")
  private Short toothNumber;

  @NotNull(message = "El precio es obligatorio")
  @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
  private BigDecimal priceCop;

  @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo")
  private BigDecimal discountCop = BigDecimal.ZERO;

  public CreateTreatmentPlanItemRequest() {
  }

  public CreateTreatmentPlanItemRequest(UUID procedureId, Short toothNumber, BigDecimal priceCop, BigDecimal discountCop) {
    this.procedureId = procedureId;
    this.toothNumber = toothNumber;
    this.priceCop = priceCop;
    this.discountCop = discountCop != null ? discountCop : BigDecimal.ZERO;
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
    this.discountCop = discountCop != null ? discountCop : BigDecimal.ZERO;
  }
}
