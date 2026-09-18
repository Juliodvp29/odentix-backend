package com.julio.odentix.odentix_backend.inventory.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Movimiento de stock sobre un {@link InventoryItem} (FASE7-03).
 *
 * <p>{@code quantityDelta} positivo = entrada, negativo = salida (nunca cero,
 * por CHECK en BD). Al insertarse, el trigger {@code trg_apply_stock_movement}
 * suma el delta al stock del ítem en la misma transacción; si el resultado
 * deja el stock en negativo, el CHECK de {@code inventory_items.quantity}
 * revierte todo — no se valida "stock suficiente" en Java.
 *
 * <p>El autor ({@code createdBy}) se modela como UUID simple, no {@code @ManyToOne}
 * (mismo patrón que {@code ClinicalRecord.professionalId}): evita cargar el grafo
 * de usuario en operaciones de inventario.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "stock_movements")
public class StockMovement extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inventory_item_id", nullable = false)
  private InventoryItem inventoryItem;

  @Column(name = "quantity_delta", nullable = false)
  private int quantityDelta;

  @Column(name = "reason", columnDefinition = "TEXT")
  private String reason;

  @Column(name = "created_by")
  private UUID createdBy;

  public StockMovement() {
    super();
  }

  public StockMovement(UUID tenantId, InventoryItem inventoryItem, int quantityDelta) {
    super(tenantId);
    this.inventoryItem = inventoryItem;
    this.quantityDelta = quantityDelta;
  }

  public InventoryItem getInventoryItem() {
    return inventoryItem;
  }

  public void setInventoryItem(InventoryItem inventoryItem) {
    this.inventoryItem = inventoryItem;
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

  public UUID getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(UUID createdBy) {
    this.createdBy = createdBy;
  }

  @Override
  public String toString() {
    return "StockMovement{"
        + "id=" + getId()
        + ", quantityDelta=" + quantityDelta
        + '}';
  }
}
