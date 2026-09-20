package com.julio.odentix.odentix_backend.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Solicitud para registrar un movimiento de stock (FASE7-04).
 *
 * <p>{@code quantityDelta} positivo = entrada, negativo = salida.
 * Sin Lombok.
 */
public class CreateStockMovementRequest {

  @NotNull(message = "quantityDelta es obligatorio")
  private Integer quantityDelta;

  private String reason;

  public CreateStockMovementRequest() {
  }

  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  public void setQuantityDelta(Integer quantityDelta) {
    this.quantityDelta = quantityDelta;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }
}

