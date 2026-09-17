-- FASE2-09: modelo de archivos de pacientes (patient_files).
-- Consistente con docs/schema.sql sección 8 (tabla patient_files), con
-- las convenciones de TenantAwareEntity:
--   - Se añaden created_at/updated_at + trigger set_updated_at() requeridos
--     por TenantAwareEntity (mismo precedente que V4, V7 y V8).
--   - uploaded_by referencia a users(id) ON DELETE SET NULL.
--   - Row Level Security (RLS) habilitado con política tenant_isolation.

CREATE TABLE patient_files (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id    UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  storage_key   TEXT NOT NULL,
  file_name     TEXT NOT NULL,
  content_type  TEXT,
  size_bytes    BIGINT CHECK (size_bytes >= 0),
  uploaded_by   UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice por tenant y paciente para listar rápidamente archivos de un paciente
CREATE INDEX idx_patient_files_patient
  ON patient_files (tenant_id, patient_id);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_patient_files_updated_at
  BEFORE UPDATE ON patient_files
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE patient_files IS
  'Metadatos de archivos adjuntos de pacientes almacenados en S3/MinIO (FASE2-09).';

-- Row Level Security (segunda capa de defensa tras el filtro @TenantId)
ALTER TABLE patient_files ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patient_files
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
