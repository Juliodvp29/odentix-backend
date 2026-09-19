-- FASE11-01: Planes del SaaS y suscripciones de tenants (plans, plan_features,
-- plan_limits, tenant_subscriptions).
-- Consistente con docs/schema.sql secciones 2 (tipos), 4 (tablas) y 17 (seeds).
--
-- Notas sobre decisiones tomadas:
--   - Estas tablas NO existían en ninguna migración Flyway previa (V1-V24
--     verificadas): el ticket asumía que ya estaban, pero solo vivían en
--     schema.sql como referencia. Se crean aquí por primera vez.
--   - plans / plan_features / plan_limits son catálogos GLOBALES (sin
--     tenant_id, sin RLS): todos los tenants leen el mismo catálogo.
--     El RLS de docs/schema.sql §16 solo aplica a tablas con tenant_id,
--     por eso aquí solo tenant_subscriptions lleva política tenant_isolation.
--   - tenant_subscriptions SÍ lleva tenant_id + RLS (mismo patrón con
--     bypass NULL de V2/V17/V24) y trigger set_updated_at.
--   - current_period_end es NOT NULL sin DEFAULT (schema.sql no define
--     default): lo calcula Java al crear la suscripción.

-- 1. Tipos enumerados (docs/schema.sql §2)
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'subscription_status') THEN
    CREATE TYPE subscription_status AS ENUM ('trialing', 'active', 'past_due', 'cancelled');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'billing_cycle') THEN
    CREATE TYPE billing_cycle AS ENUM ('monthly', 'annual');
  END IF;
END;
$$;

-- 2. Catálogo global de planes
CREATE TABLE plans (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code               TEXT NOT NULL UNIQUE,
  name               TEXT NOT NULL,
  monthly_price_cop  NUMERIC(12,2) NOT NULL CHECK (monthly_price_cop >= 0),
  annual_price_cop   NUMERIC(12,2) NOT NULL CHECK (annual_price_cop >= 0),
  is_active          BOOLEAN NOT NULL DEFAULT true,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE plans IS
  'Catálogo global de planes del SaaS (FASE11-01). No es tenant-specific: sin tenant_id ni RLS.';

CREATE TRIGGER trg_plans_updated_at
  BEFORE UPDATE ON plans
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 3. Feature flags por plan
CREATE TABLE plan_features (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id      UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  feature_key  TEXT NOT NULL,
  enabled      BOOLEAN NOT NULL DEFAULT false,
  UNIQUE (plan_id, feature_key)
);

COMMENT ON COLUMN plan_features.feature_key IS
  'Claves usadas por la app: crm_leads, cartera, automations_full, specialists, '
  'inventory, inventory_alerts, opportunities_engine, ai_assistant.';

-- 4. Límites numéricos por plan (NULL = ilimitado)
CREATE TABLE plan_limits (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id     UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  limit_key   TEXT NOT NULL,
  max_value   INTEGER,
  UNIQUE (plan_id, limit_key)
);

COMMENT ON COLUMN plan_limits.limit_key IS
  'Claves usadas por la app: max_sedes, max_users, max_patients, '
  'whatsapp_conversations_month, max_specialists, opportunities_max_rules. NULL = ilimitado.';

-- 5. Suscripción del tenant (tabla de negocio: con tenant_id + RLS)
CREATE TABLE tenant_subscriptions (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  plan_id                UUID NOT NULL REFERENCES plans(id),
  status                 subscription_status NOT NULL DEFAULT 'trialing',
  billing_cycle          billing_cycle NOT NULL DEFAULT 'monthly',
  current_period_start   TIMESTAMPTZ NOT NULL DEFAULT now(),
  current_period_end     TIMESTAMPTZ NOT NULL,
  cancel_at_period_end   BOOLEAN NOT NULL DEFAULT false,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_subscriptions_tenant ON tenant_subscriptions (tenant_id);

-- Un tenant no puede tener dos suscripciones "vivas" al mismo tiempo.
CREATE UNIQUE INDEX uq_tenant_subscriptions_active
  ON tenant_subscriptions (tenant_id)
  WHERE status IN ('trialing', 'active', 'past_due');

CREATE TRIGGER trg_tenant_subscriptions_updated_at
  BEFORE UPDATE ON tenant_subscriptions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE tenant_subscriptions IS
  'Suscripción del tenant a un plan (FASE11-01). Tabla de negocio: filtra por tenant_id.';

-- Row Level Security (mismo patrón con bypass NULL que V2/V17/V24,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE tenant_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tenant_subscriptions
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);


-- =============================================================================
-- 6. DATOS SEMILLA — Planes, features y límites (docs/schema.sql §17)
-- =============================================================================
-- Precios: Esencial $99.900, Profesional $169.900, Clínica $259.900 COP/mes.
-- El precio anual equivale a "10 meses" (~2 meses gratis).

INSERT INTO plans (code, name, monthly_price_cop, annual_price_cop) VALUES
  ('esencial',    'Esencial',    99900.00,  999000.00),
  ('profesional', 'Profesional', 169900.00, 1699000.00),
  ('clinica',     'Clínica',     259900.00, 2599000.00);

-- Feature flags
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key, enabled)
SELECT p.id, f.feature_key, f.enabled FROM (
  VALUES
    ('esencial',    'crm_leads',            false),
    ('esencial',    'cartera',              false),
    ('esencial',    'automations_full',     false),
    ('esencial',    'specialists',          false),
    ('esencial',    'inventory',            false),
    ('esencial',    'inventory_alerts',     false),
    ('esencial',    'opportunities_engine', false),
    ('esencial',    'ai_assistant',         false),

    ('profesional', 'crm_leads',            true),
    ('profesional', 'cartera',              true),
    ('profesional', 'automations_full',     true),
    ('profesional', 'specialists',          true),
    ('profesional', 'inventory',            true),
    ('profesional', 'inventory_alerts',     false),
    ('profesional', 'opportunities_engine', true),
    ('profesional', 'ai_assistant',         false),

    ('clinica',     'crm_leads',            true),
    ('clinica',     'cartera',              true),
    ('clinica',     'automations_full',     true),
    ('clinica',     'specialists',          true),
    ('clinica',     'inventory',            true),
    ('clinica',     'inventory_alerts',     true),
    ('clinica',     'opportunities_engine', true),
    ('clinica',     'ai_assistant',         true)
) AS f(plan_code, feature_key, enabled)
JOIN p ON p.code = f.plan_code;

-- Límites numéricos (NULL = ilimitado)
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_limits (plan_id, limit_key, max_value)
SELECT p.id, l.limit_key, l.max_value FROM (
  VALUES
    ('esencial',    'max_sedes',                       1),
    ('esencial',    'max_users',                       2),
    ('esencial',    'max_patients',                    150),
    ('esencial',    'whatsapp_conversations_month',    0),
    ('esencial',    'max_specialists',                 0),
    ('esencial',    'opportunities_max_rules',         0),

    ('profesional', 'max_sedes',                       2),
    ('profesional', 'max_users',                       6),
    ('profesional', 'max_patients',                    800),
    ('profesional', 'whatsapp_conversations_month',    300),
    ('profesional', 'max_specialists',                 2),
    ('profesional', 'opportunities_max_rules',         2),

    ('clinica',     'max_sedes',                       NULL),
    ('clinica',     'max_users',                       NULL),
    ('clinica',     'max_patients',                    NULL),
    ('clinica',     'whatsapp_conversations_month',    1000),
    ('clinica',     'max_specialists',                 NULL),
    ('clinica',     'opportunities_max_rules',         NULL)
) AS l(plan_code, limit_key, max_value)
JOIN p ON p.code = l.plan_code;
