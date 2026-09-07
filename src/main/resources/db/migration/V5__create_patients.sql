-- FASE2-01: primera entidad de negocio real (patients).
-- Consistente con docs/schema.sql sección 8 (tabla patients, índices y RLS),
-- con una desviación documentada:
--   - No se crea el trigger trg_audit_patients de schema.sql: la función
--     audit_sensitive_change() no existe en las migraciones y la auditoría
--     se hace desde Java vía AuditService (FASE1-13/14), no con triggers DB.
-- El índice trigram de búsqueda por nombre se incluye desde ya para no
-- necesitar otra migración en FASE2-03 (solo el endpoint lo usará entonces).

-- Búsqueda por nombre con ILIKE eficiente (docs/schema.sql §1).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Tabla de pacientes (alcance reducido: sin odontograma ni historia clínica,
-- eso llega en FASE2-05/07 con sus propias tablas y migraciones).
CREATE TABLE patients (
  id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                 UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  document_type             TEXT,
  document_number           TEXT,
  first_name                TEXT NOT NULL,
  last_name                 TEXT NOT NULL,
  birth_date                DATE,
  phone                     TEXT,
  email                     CITEXT,
  address                   TEXT,
  emergency_contact_name    TEXT,
  emergency_contact_phone   TEXT,
  is_active                 BOOLEAN NOT NULL DEFAULT true,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_tenant_active ON patients (tenant_id, is_active);

-- Documento único por tenant, solo cuando se registra (permite pacientes
-- sin documento capturado todavía, ej. un lead recién convertido).
CREATE UNIQUE INDEX uq_patients_tenant_document
  ON patients (tenant_id, document_number)
  WHERE document_number IS NOT NULL;

-- Buscador de FASE2-03: fragmentos de "nombre + apellido" sin distinguir
-- mayúsculas/acentos exactos.
CREATE INDEX idx_patients_name_trgm
  ON patients USING gin ((first_name || ' ' || last_name) gin_trgm_ops);

-- Trigger para mantener updated_at automáticamente (convención global).
CREATE TRIGGER trg_patients_updated_at
  BEFORE UPDATE ON patients
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE patients IS
  'Primera entidad de negocio real (FASE2-01). Toda fila pertenece a un tenant.';

-- Row Level Security sobre patients (mismo patrón que users/audit_log,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE patients ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON patients
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
