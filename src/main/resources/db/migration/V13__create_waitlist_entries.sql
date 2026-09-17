-- FASE3-06: lista de espera (waitlist_entries).
-- Consistente con docs/schema.sql (tipo waitlist_status, tabla
-- waitlist_entries, índice por tenant/estado), con una desviación
-- documentada:
--   - procedure_id se crea nullable SIN FK porque no existe tabla de
--     procedimientos todavía (mismo precedente que
--     Appointment.procedure_id en FASE3-02). La constraint FK se añadirá
--     en la migración correspondiente cuando exista el catálogo.

-- Estados de un interesado: activo, ya contactado, convertido en cita o
-- descartado (los mueve FASE3-07; toda entrada nace 'activa').
CREATE TYPE waitlist_status AS ENUM (
  'activa',
  'contactado',
  'convertida',
  'descartada'
);

CREATE TABLE waitlist_entries (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id     UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  procedure_id   UUID,              -- FK pendiente hasta que exista el catálogo
  desired_from   TIMESTAMPTZ,       -- inicio del rango de fechas deseado (opcional)
  desired_to     TIMESTAMPTZ,       -- fin del rango de fechas deseado (opcional)
  status         waitlist_status NOT NULL DEFAULT 'activa',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Insumo de FASE3-07: candidatos activos por tenant.
CREATE INDEX idx_waitlist_tenant_status ON waitlist_entries (tenant_id, status);

-- Trigger para mantener updated_at automáticamente (convención global,
-- misma función set_updated_at() usada en todas las tablas de negocio).
CREATE TRIGGER trg_waitlist_entries_updated_at
  BEFORE UPDATE ON waitlist_entries
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE waitlist_entries IS
  'Lista de espera por paciente (FASE3-06). Cada fila pertenece a un tenant.';

-- Row Level Security (mismo patrón que appointments,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE waitlist_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON waitlist_entries
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
