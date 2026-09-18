-- FASE6-01: cartera y pagos por etapas (payment_plans, installments).
-- Consistente con docs/schema.sql sección 11, con las mismas desviaciones
-- documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15).
--   - created_at/updated_at añadidos donde los exige TenantAwareEntity
--     (precedente de V4, V8, V15).
--
-- La función mark_overdue_installments() se define aquí; la invoca el
-- @Scheduled job de FASE6-03, no un trigger (el vencimiento depende del
-- paso del tiempo, no de un evento de escritura — nota en schema.sql §11).

-- Estado de una cuota: pendiente → pagada (o vencida por el job diario).
CREATE TYPE installment_status AS ENUM (
  'pendiente',
  'pagada',
  'vencida'
);

CREATE TABLE payment_plans (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  treatment_plan_id   UUID NOT NULL REFERENCES treatment_plans(id) ON DELETE CASCADE,
  total_amount_cop    NUMERIC(12,2) NOT NULL CHECK (total_amount_cop >= 0),
  installments_count  INTEGER NOT NULL CHECK (installments_count > 0),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_plans_treatment_plan ON payment_plans (treatment_plan_id);

CREATE TRIGGER trg_payment_plans_updated_at
  BEFORE UPDATE ON payment_plans
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE payment_plans IS
  'Plan de pago en cuotas asociado a un plan de tratamiento (FASE6-01).';


CREATE TABLE installments (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  payment_plan_id     UUID NOT NULL REFERENCES payment_plans(id) ON DELETE CASCADE,
  installment_number  INTEGER NOT NULL CHECK (installment_number > 0),
  amount_cop          NUMERIC(12,2) NOT NULL CHECK (amount_cop > 0),
  due_date            DATE NOT NULL,
  status              installment_status NOT NULL DEFAULT 'pendiente',
  paid_at             TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_installments_plan_number UNIQUE (payment_plan_id, installment_number)
);

-- Insumo directo del dashboard de cartera (FASE6-04): consultar cuánto
-- hay pendiente/vencido/por vencer por tenant.
CREATE INDEX idx_installments_tenant_status_due
  ON installments (tenant_id, status, due_date);

CREATE TRIGGER trg_installments_updated_at
  BEFORE UPDATE ON installments
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE installments IS
  'Cuotas individuales de un plan de pago (FASE6-01). El job diario de FASE6-03 marca las vencidas.';


-- Se ejecuta periódicamente por el @Scheduled job de FASE6-03.
-- Retorna el número de cuotas que pasaron a "vencida" en esa corrida
-- (útil para logging/métricas del job).
CREATE OR REPLACE FUNCTION mark_overdue_installments()
RETURNS INTEGER AS $$
DECLARE
  v_affected INTEGER;
BEGIN
  UPDATE installments
     SET status = 'vencida'
   WHERE status = 'pendiente'
     AND due_date < CURRENT_DATE;

  GET DIAGNOSTICS v_affected = ROW_COUNT;
  RETURN v_affected;
END;
$$ LANGUAGE plpgsql;


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE payment_plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON payment_plans
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE installments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON installments
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
