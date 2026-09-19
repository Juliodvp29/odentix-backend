-- FASE8-01: tareas (tasks).
-- Consistente con docs/schema.sql sección 14, con las mismas desviaciones
-- documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15, V18–V20).
--
-- La referencia polimórfica related_entity_type/id es intencional y NO lleva
-- FK real (puede apuntar a leads, treatment_plans, appointments, etc.; ver
-- nota en schema.sql §14): la integridad se garantiza desde la aplicación.

CREATE TYPE task_status AS ENUM (
  'pendiente',
  'en_progreso',
  'completada',
  'cancelada'
);

CREATE TYPE task_priority AS ENUM (
  'baja',
  'media',
  'alta'
);

CREATE TABLE tasks (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  title                TEXT NOT NULL,
  description          TEXT,
  related_entity_type  TEXT,
  related_entity_id    UUID,
  assigned_to          UUID REFERENCES users(id) ON DELETE SET NULL,
  due_at               TIMESTAMPTZ,
  priority             task_priority NOT NULL DEFAULT 'media',
  status               task_status NOT NULL DEFAULT 'pendiente',
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tasks_tenant_assignee_status ON tasks (tenant_id, assigned_to, status);
CREATE INDEX idx_tasks_related_entity ON tasks (related_entity_type, related_entity_id);

CREATE TRIGGER trg_tasks_updated_at
  BEFORE UPDATE ON tasks
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE tasks IS
  'Tareas operativas y automáticas de la clínica (FASE8-01). related_entity es polimórfico intencional, sin FK.';


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON tasks
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
