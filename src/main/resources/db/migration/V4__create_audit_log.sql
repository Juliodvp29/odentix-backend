-- FASE1-13: tabla y entidad de auditoría (audit_log).
-- Consistente con docs/schema.sql (tabla audit_log, tipo audit_action, índices,
-- política RLS tenant_isolation), con dos desviaciones documentadas:
--   1. PK UUID (gen_random_uuid()) en vez de BIGSERIAL: la entidad hereda de
--      TenantAwareEntity y así obtiene el filtro automático @TenantId (FASE1-09).
--   2. Columna updated_at + trigger set_updated_at(): la exige el mapeo de la
--      clase base. La tabla sigue siendo append-only por convención: el
--      repositorio y el servicio no exponen update ni delete.

-- Tipo enumerado de acciones (docs/schema.sql §2)
CREATE TYPE audit_action AS ENUM (
  'insert',
  'update',
  'delete',
  'login_success',
  'login_failed'
);

-- Tabla de auditoría (append-only)
CREATE TABLE audit_log (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
  action       audit_action NOT NULL,
  entity_name  TEXT NOT NULL,
  entity_id    UUID,
  detail       JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (tenant_id, entity_name, entity_id);

-- Trigger para mantener updated_at automáticamente (heredado de la convención base)
CREATE TRIGGER trg_audit_log_updated_at
  BEFORE UPDATE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE audit_log IS
  'Tabla append-only. Nunca se actualiza ni se borra un registro existente.';

-- Row Level Security sobre audit_log (mismo patrón que users, docs/schema.sql §16)
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON audit_log
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
