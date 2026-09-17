-- FASE3-01: Modelar Professional y Room.
-- Consistente con docs/schema.sql sección 6 (tablas professionals y rooms).
-- Incluye:
--   - professionals con vínculo opcional a users (user_id nullable, con uq_professionals_user).
--   - rooms con constraint UNIQUE (tenant_id, name).
--   - RLS y triggers de updated_at para ambas tablas.
--   - Claves foráneas diferidas en V7 y V8:
--       * clinical_records.professional_id -> professionals(id)
--       * odontogram_entries.recorded_by   -> professionals(id)

-- =============================================================================
-- 1. TABLA PROFESSIONALS
-- =============================================================================
CREATE TABLE professionals (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
  full_name       TEXT NOT NULL,
  specialty       TEXT,
  license_number  TEXT,
  is_external     BOOLEAN NOT NULL DEFAULT false,
  is_active       BOOLEAN NOT NULL DEFAULT true,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_professionals_tenant ON professionals (tenant_id);

-- Un usuario del sistema corresponde a lo sumo a un profesional por clínica.
CREATE UNIQUE INDEX uq_professionals_user
  ON professionals (user_id)
  WHERE user_id IS NOT NULL;

CREATE TRIGGER trg_professionals_updated_at
  BEFORE UPDATE ON professionals
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE professionals IS
  'Profesionales y odontólogos de la clínica (FASE3-01). Pueden o no tener usuario en el sistema.';

ALTER TABLE professionals ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON professionals
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- =============================================================================
-- 2. TABLA ROOMS
-- =============================================================================
CREATE TABLE rooms (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, name)
);

CREATE INDEX idx_rooms_tenant ON rooms (tenant_id);

CREATE TRIGGER trg_rooms_updated_at
  BEFORE UPDATE ON rooms
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE rooms IS
  'Consultorios o espacios físicos de atención de la clínica (FASE3-01).';

ALTER TABLE rooms ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON rooms
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- =============================================================================
-- 3. CONSTRAINTS DE CLAVES FORÁNEAS DIFERIDAS (V7 y V8)
-- =============================================================================
ALTER TABLE clinical_records
  ADD CONSTRAINT fk_clinical_records_professional
  FOREIGN KEY (professional_id) REFERENCES professionals(id) ON DELETE SET NULL;

ALTER TABLE odontogram_entries
  ADD CONSTRAINT fk_odontogram_entries_recorded_by
  FOREIGN KEY (recorded_by) REFERENCES professionals(id) ON DELETE SET NULL;
