package com.julio.odentix.odentix_backend.billing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

/**
 * Ítem manual de una factura (FASE4-04).
 */
public class InvoiceItemInput {

  @NotBlank(message = "La descripción del ítem es obligatoria")
  private String description;

  @Min(value = 1, message = "La cantidad debe ser al menos 1")
  private int quantity = 1;

  @NotNull(message = "El precio unitario es obligatorio")
  @PositiveOrZero(message = "El precio unitario no puede ser negativo")
  private BigDecimal unitPriceCop;

  public InvoiceItemInput() {
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPriceCop() {
    return unitPriceCop;
  }

  public void setUnitPriceCop(BigDecimal unitPriceCop) {
    this.unitPriceCop = unitPriceCop;
  }
}
