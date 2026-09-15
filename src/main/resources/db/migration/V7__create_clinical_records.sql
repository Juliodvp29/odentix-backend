-- FASE2-05: historia clínica básica (clinical_records).
-- Consistente con docs/schema.sql sección 8 (tabla clinical_records),
-- con dos desviaciones documentadas:
--   - professional_id se crea nullable SIN FK porque la tabla professionals
--     no existe todavía (se crea en Fase 3, FASE3-01). La constraint FK se
--     añadirá en la migración correspondiente.
--   - No se crea el trigger trg_audit_clinical_records de schema.sql: la
--     auditoría se gestiona desde Java vía AuditService (FASE1-13/14),
--     misma decisión tomada en V5 para patients.

CREATE TABLE clinical_records (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id  UUID,           -- FK pendiente hasta Fase 3
  chief_complaint  TEXT,           -- motivo de consulta
  anamnesis        TEXT,           -- antecedentes / historia de la enfermedad actual
  diagnosis        TEXT,           -- diagnóstico
  evolution        TEXT,           -- evolución / notas de seguimiento
  recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice principal para listar registros clínicos de un paciente en orden
-- cronológico descendente (el más reciente primero).
CREATE INDEX idx_clinical_records_patient ON clinical_records (patient_id, recorded_at DESC);

-- Índice sobre tenant_id para RLS y queries filtradas por tenant.
CREATE INDEX idx_clinical_records_tenant ON clinical_records (tenant_id);

-- Trigger para mantener updated_at automáticamente (convención global,
-- misma función set_updated_at() usada en todas las tablas de negocio).
CREATE TRIGGER trg_clinical_records_updated_at
  BEFORE UPDATE ON clinical_records
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE clinical_records IS
  'Historia clínica básica por paciente (FASE2-05). Cada fila pertenece a un tenant.';

-- Row Level Security (mismo patrón que patients/users/audit_log,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE clinical_records ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON clinical_records
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
