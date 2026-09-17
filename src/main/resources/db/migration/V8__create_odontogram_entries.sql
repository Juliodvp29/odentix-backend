-- FASE2-07: modelo de odontograma (odontogram_entries).
-- Consistente con docs/schema.sql sección 8 (tabla odontogram_entries,
-- tipo odontogram_entry_type e índice por paciente/pieza), con dos
-- desviaciones documentadas:
--   - recorded_by se crea nullable SIN FK porque la tabla professionals
--     no existe todavía (se crea en Fase 3, FASE3-01). La constraint FK se
--     añadirá en la migración correspondiente (mismo precedente que V7
--     para clinical_records.professional_id).
--   - Se añaden created_at/updated_at + trigger set_updated_at(): las
--     exige el mapeo de TenantAwareEntity (mismo precedente que V4 para
--     audit_log). La tabla no es append-only por convención: cada entrada
--     es un momento clínico distinto, nunca se reescribe una existente.
--   - No se crea trigger de auditoría: se gestiona desde Java vía
--     AuditService (mismo precedente que V5/V7).

-- Tipo enumerado: separa estado actual, diagnóstico, plan propuesto y
-- tratamiento realizado (sección 8.4 del doc de arquitectura).
CREATE TYPE odontogram_entry_type AS ENUM (
  'estado_actual',
  'diagnostico',
  'plan_propuesto',
  'tratamiento_realizado'
);

CREATE TABLE odontogram_entries (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id    UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  tooth_number  SMALLINT NOT NULL CHECK (tooth_number BETWEEN 11 AND 48), -- notación FDI
  surface       TEXT,                                -- ej. 'oclusal', 'mesial'
  entry_type    odontogram_entry_type NOT NULL,
  condition     TEXT NOT NULL,                       -- ej. 'caries', 'obturado', 'ausente'
  recorded_by   UUID,              -- FK pendiente hasta Fase 3 (ver cabecera)
  recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes         TEXT,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice principal: entradas de un paciente por pieza (base del GET de FASE2-08).
CREATE INDEX idx_odontogram_patient_tooth
  ON odontogram_entries (tenant_id, patient_id, tooth_number);

-- Trigger para mantener updated_at automáticamente (convención global,
-- misma función set_updated_at() usada en todas las tablas de negocio).
CREATE TRIGGER trg_odontogram_entries_updated_at
  BEFORE UPDATE ON odontogram_entries
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE odontogram_entries IS
  'Odontograma por paciente (FASE2-07). Cada fila es un momento clínico distinto; varias entradas pueden referirse a la misma pieza sin sobrescribirse.';

-- Row Level Security (mismo patrón que patients/clinical_records,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE odontogram_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON odontogram_entries
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
