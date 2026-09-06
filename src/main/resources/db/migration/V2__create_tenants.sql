-- FASE1-01: Modelo de Tenant (clínica cliente, raíz del modelo multi-tenant).
-- Consistente con docs/schema.sql secciones 1, 2, 3 y 4.

-- Extensiones requeridas por el modelo
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS citext;

-- Función de utilidad para RLS multi-tenant (docs/schema.sql §3).
-- Retorna el UUID del tenant configurado en la sesión o NULL si no está seteado.
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS UUID AS $$
  SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;

-- Tipo enumerado para el estado del tenant (docs/schema.sql §2)
CREATE TYPE tenant_status AS ENUM ('trial', 'active', 'suspended', 'cancelled');

-- Tabla raíz del modelo multi-tenant
CREATE TABLE tenants (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  tax_id      TEXT,
  status      tenant_status NOT NULL DEFAULT 'trial',
  timezone    TEXT NOT NULL DEFAULT 'America/Bogota',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenants_status ON tenants (status);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_tenants_updated_at
  BEFORE UPDATE ON tenants
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Row Level Security sobre tenants (docs/schema.sql §4)
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_self_isolation ON tenants
  USING (id = current_tenant_id() OR current_tenant_id() IS NULL);
