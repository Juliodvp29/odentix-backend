-- FASE8-03: notificaciones (notifications).
-- Consistente con docs/schema.sql sección 14, con las mismas desviaciones
-- documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15, V18–V21).
--   - updated_at añadido aunque schema.sql no lo trae: lo exige
--     TenantAwareEntity (precedente de V4, V8, V15, V18, V20).

CREATE TYPE notification_channel AS ENUM (
  'email',
  'whatsapp',
  'sms'
);

CREATE TYPE notification_status AS ENUM (
  'pendiente',
  'enviada',
  'fallida'
);

CREATE TABLE notifications (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id     UUID REFERENCES patients(id) ON DELETE SET NULL,
  channel        notification_channel NOT NULL,
  recipient      TEXT NOT NULL,
  template_key   TEXT,
  payload        JSONB,
  status         notification_status NOT NULL DEFAULT 'pendiente',
  sent_at        TIMESTAMPTZ,
  error_detail   TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_tenant_status ON notifications (tenant_id, status);

CREATE TRIGGER trg_notifications_updated_at
  BEFORE UPDATE ON notifications
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE notifications IS
  'Registro de cada intento de notificación (FASE8-03). El fallo de un canal se registra con fallida + error_detail y nunca bloquea el flujo que lo originó.';


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notifications
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
