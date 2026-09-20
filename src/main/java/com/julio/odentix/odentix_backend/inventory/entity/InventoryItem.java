package com.julio.odentix.odentix_backend.inventory.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Ítem de inventario de la clínica (FASE7-03).
 *
 * <p>El campo {@code quantity} <b>solo</b> lo modifica el trigger
 * {@code trg_apply_stock_movement} (V20) al insertar un {@link StockMovement}:
 * Java nunca recalcula el stock a mano. Después de registrar un movimiento, la
 * entidad en memoria queda con el valor anterior — hay que re-leer
 * ({@code EntityManager.refresh} o nueva búsqueda) para ver el stock aplicado.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 * El nombre es único por tenant (permite el mismo insumo en clínicas distintas).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(
    name = "inventory_items",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_inventory_items_tenant_name",
            columnNames = {"tenant_id", "name"})
    }
)
public class InventoryItem extends TenantAwareEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "unit")
  private String unit;

  @Column(name = "quantity", nullable = false)
  private int quantity = 0;

  @Column(name = "min_threshold", nullable = false)
  private int minThreshold = 0;

  public InventoryItem() {
    super();
  }

  public InventoryItem(UUID tenantId, String name) {
    super(tenantId);
    this.name = name;
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

  @Override
  public String toString() {
    return "InventoryItem{"
        + "id=" + getId()
        + ", name='" + name + '\''
        + ", quantity=" + quantity
        + '}';
  }
}

