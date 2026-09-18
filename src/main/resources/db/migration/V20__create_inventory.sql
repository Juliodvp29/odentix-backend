-- FASE7-03: inventario (inventory_items, stock_movements).
-- Consistente con docs/schema.sql sección 13, con las mismas desviaciones
-- documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15, V18, V19).
--   - created_at/updated_at añadidos donde los exige TenantAwareEntity:
--     stock_movements gana updated_at respecto a schema.sql (precedente
--     de V4, V8, V15, V18).
--
-- El trigger trg_apply_stock_movement es el único mecanismo que mueve el
-- stock: Java nunca recalcula quantity (regla expresa del ticket — el CHECK
-- de quantity >= 0 revierte la transacción si un consumo deja negativo).

-- =============================================================================
-- 1. TABLA INVENTORY_ITEMS
-- =============================================================================
CREATE TABLE inventory_items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  unit           TEXT,
  quantity       INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  min_threshold  INTEGER NOT NULL DEFAULT 0 CHECK (min_threshold >= 0),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, name)
);

-- Insumo directo de la Fase 9 (regla "inventario crítico", roadmap FASE9-02).
CREATE INDEX idx_inventory_items_critical
  ON inventory_items (tenant_id)
  WHERE quantity <= min_threshold;

CREATE TRIGGER trg_inventory_items_updated_at
  BEFORE UPDATE ON inventory_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE inventory_items IS
  'Insumos e inventario de la clínica (FASE7-03). El stock solo se mueve vía stock_movements + trigger.';


-- =============================================================================
-- 2. TABLA STOCK_MOVEMENTS
-- =============================================================================
CREATE TABLE stock_movements (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  inventory_item_id   UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
  quantity_delta      INTEGER NOT NULL CHECK (quantity_delta <> 0),
  reason              TEXT,
  created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_item
  ON stock_movements (inventory_item_id, created_at DESC);

CREATE TRIGGER trg_stock_movements_updated_at
  BEFORE UPDATE ON stock_movements
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE stock_movements IS
  'Movimientos de stock: quantity_delta positivo = entrada, negativo = salida (FASE7-03).';


-- =============================================================================
-- 3. TRIGGER apply_stock_movement (reutilizado tal cual de docs/schema.sql)
-- =============================================================================
-- Aplica el movimiento al stock automáticamente. Si el resultado deja el
-- stock en negativo, el CHECK de inventory_items.quantity revierte toda
-- la transacción (no hace falta validar "stock suficiente" a mano).
-- Además exige coincidencia de tenant entre movimiento e ítem: un movimiento
-- de otro tenant no encuentra la fila y falla en vez de mover stock ajeno.
CREATE OR REPLACE FUNCTION apply_stock_movement()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE inventory_items
     SET quantity = quantity + NEW.quantity_delta
   WHERE id = NEW.inventory_item_id
     AND tenant_id = NEW.tenant_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'inventory_item % no encontrado para el tenant %',
      NEW.inventory_item_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_apply_stock_movement
AFTER INSERT ON stock_movements
FOR EACH ROW EXECUTE FUNCTION apply_stock_movement();


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16, precedente de V11, V18 y V19). Segunda capa de
-- defensa tras el filtro @TenantId de Hibernate.
ALTER TABLE inventory_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON inventory_items
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE stock_movements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON stock_movements
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
