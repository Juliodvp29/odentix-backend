-- FASE4-03: facturación simple (invoices, invoice_items, payments).
-- Consistente con docs/schema.sql sección 10 (tipos invoice_status y
-- payment_method, columnas, constraints, índices), con dos desviaciones
-- documentadas:
--   - No se crean los triggers trg_audit_invoices/trg_audit_payments de
--     schema.sql: la auditoría se gestiona desde Java vía AuditService
--     (mismo precedente que V5/V7/V8).
--   - Se añaden created_at/updated_at a invoice_items y payments donde
--     schema.sql no los tiene: los exige el mapeo de TenantAwareEntity
--     (mismo precedente que V4 para audit_log y V8 para odontogram_entries).
--
-- Fuera de alcance (FASE4-04): generación de invoice_number y cálculo de
-- totales desde el servicio; aquí solo el modelo y sus constraints.

-- Estados de una factura: pendiente → parcial → pagada (o anulada).
CREATE TYPE invoice_status AS ENUM (
  'pendiente',
  'parcial',
  'pagada',
  'anulada'
);

-- Medios de pago aceptados.
CREATE TYPE payment_method AS ENUM (
  'efectivo',
  'tarjeta',
  'transferencia',
  'otro'
);

CREATE TABLE invoices (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  -- RESTRICT a propósito (igual que schema.sql): un paciente con facturas
  -- no se puede borrar; primero hay que resolver su cartera.
  patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
  treatment_plan_id   UUID REFERENCES treatment_plans(id) ON DELETE SET NULL,
  invoice_number      TEXT NOT NULL,
  status              invoice_status NOT NULL DEFAULT 'pendiente',
  subtotal_cop        NUMERIC(12,2) NOT NULL CHECK (subtotal_cop >= 0),
  discount_cop        NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_cop >= 0),
  total_cop           NUMERIC(12,2) NOT NULL CHECK (total_cop >= 0),
  issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_invoices_tenant_number UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_invoices_patient ON invoices (patient_id);
CREATE INDEX idx_invoices_tenant_status ON invoices (tenant_id, status);

CREATE TRIGGER trg_invoices_updated_at
  BEFORE UPDATE ON invoices
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE invoices IS
  'Facturas simples por paciente (FASE4-03). Sin facturación electrónica todavía.';


CREATE TABLE invoice_items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id     UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  description    TEXT NOT NULL,
  quantity       INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price_cop NUMERIC(12,2) NOT NULL CHECK (unit_price_cop >= 0),
  total_cop      NUMERIC(12,2) GENERATED ALWAYS AS (quantity * unit_price_cop) STORED,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);

CREATE TRIGGER trg_invoice_items_updated_at
  BEFORE UPDATE ON invoice_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE invoice_items IS
  'Ítems de factura (FASE4-03). total_cop lo calcula la BD, nunca Java.';


CREATE TABLE payments (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  amount_cop   NUMERIC(12,2) NOT NULL CHECK (amount_cop > 0),
  method       payment_method NOT NULL,
  reference    TEXT,
  paid_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);

CREATE TRIGGER trg_payments_updated_at
  BEFORE UPDATE ON payments
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE payments IS
  'Pagos contra facturas (FASE4-03). El cambio de estado de la factura según pagos acumulados llega en FASE4-04.';

-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON invoices
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE invoice_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON invoice_items
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payments
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
