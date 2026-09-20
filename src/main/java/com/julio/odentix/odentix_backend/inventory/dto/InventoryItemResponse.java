package com.julio.odentix.odentix_backend.inventory.dto;

import java.util.UUID;

/**
 * Ítem de inventario con su stock actual (FASE7-04). Sin Lombok.
 */
public class InventoryItemResponse {

  private UUID id;
  private String name;
  private String unit;
  private int quantity;
  private int minThreshold;

  public InventoryItemResponse() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public int getMinThreshold() {
    return minThreshold;
  }

  public void setMinThreshold(int minThreshold) {
    this.minThreshold = minThreshold;
  }
}

