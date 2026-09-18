package com.julio.odentix.odentix_backend.inventory.dto;

import jakarta.validation.constraints.Min;

/**
 * Solicitud para actualizar un ítem de inventario (FASE7-04).
 *
 * <p>Todos los campos son opcionales (PATCH): solo los no nulos se aplican.
 * El stock no se edita por aquí — solo vía movimientos. Sin Lombok (§9 AGENTS.md).
 */
public class UpdateInventoryItemRequest {

  private String name;

  private String unit;

  @Min(value = 0, message = "minThreshold no puede ser negativo")
  private Integer minThreshold;

  public UpdateInventoryItemRequest() {
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

  public Integer getMinThreshold() {
    return minThreshold;
  }

  public void setMinThreshold(Integer minThreshold) {
    this.minThreshold = minThreshold;
  }
}
