package com.julio.odentix.odentix_backend.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para crear un ítem de inventario (FASE7-04).
 *
 * <p>El stock siempre nace en cero; las existencias iniciales se registran
 * con un movimiento de entrada. Convención: Sin Lombok (§9 de AGENTS.md).
 */
public class CreateInventoryItemRequest {

  @NotBlank(message = "name es obligatorio")
  private String name;

  private String unit;

  @Min(value = 0, message = "minThreshold no puede ser negativo")
  private int minThreshold = 0;

  public CreateInventoryItemRequest() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = unit;
  }

  public int getMinThreshold() {
    return minThreshold;
  }

  public void setMinThreshold(int minThreshold) {
    this.minThreshold = minThreshold;
  }
}
