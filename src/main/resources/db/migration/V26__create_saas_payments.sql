-- FASE11-04: cobros del propio SaaS vía Bold (saas_payments).
-- Bold.co no tiene suscripciones recurrentes nativas: cada ciclo se cobra con
-- un link de pago (API Link de pagos) que la clínica paga, y el webhook
-- confirma y extiende el periodo. Sin triggers de auditoría (AuditService
-- desde Java) y con updated_at por TenantAwareEntity (convención del proyecto).

CREATE TYPE saas_payment_status AS ENUM (
  'pendiente',
  'pagada',
  'rechazada'
);

CREATE TABLE saas_payments (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  subscription_id        UUID NOT NULL REFERENCES tenant_subscriptions(id) ON DELETE CASCADE,
  bold_reference         TEXT NOT NULL UNIQUE,
  bold_payment_link      TEXT,
  bold_notification_id   TEXT UNIQUE,
  amount_cop             NUMERIC(12,2) NOT NULL CHECK (amount_cop > 0),
  billing_cycle          billing_cycle NOT NULL,
  status                 saas_payment_status NOT NULL DEFAULT 'pendiente',
  paid_at                TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_saas_payments_subscription ON saas_payments (subscription_id, created_at DESC);
CREATE INDEX idx_saas_payments_reference ON saas_payments (bold_reference);

CREATE TRIGGER trg_saas_payments_updated_at
  BEFORE UPDATE ON saas_payments
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE saas_payments IS
  'Cobros del SaaS a las clínicas vía Bold (FASE11-04). bold_notification_id da idempotencia ante reintentos del webhook.';


-- Row Level Security (patrón del resto de tablas de negocio, schema.sql §16).
ALTER TABLE saas_payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON saas_payments
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
