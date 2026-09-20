package com.julio.odentix.odentix_backend.inventory.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Movimiento de stock registrado (FASE7-04). Sin Lombok.
 */
public class StockMovementResponse {

  private UUID id;
  private UUID inventoryItemId;
  private int quantityDelta;
  private String reason;
  private int resultingQuantity;
  private Instant createdAt;

  public StockMovementResponse() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getInventoryItemId() {
    return inventoryItemId;
  }

  public void setInventoryItemId(UUID inventoryItemId) {
    this.inventoryItemId = inventoryItemId;
  }

  public int getQuantityDelta() {
    return quantityDelta;
  }

  public void setQuantityDelta(int quantityDelta) {
    this.quantityDelta = quantityDelta;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public int getResultingQuantity() {
    return resultingQuantity;
  }

  public void setResultingQuantity(int resultingQuantity) {
    this.resultingQuantity = resultingQuantity;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}

