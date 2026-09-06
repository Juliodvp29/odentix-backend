-- FASE1-02 / FASE1-03: Modelo de User, roles y constraint de email por tenant.
-- Consistente con docs/schema.sql secciones 2, 5 y 16.

-- Tipo enumerado para roles de usuario (docs/schema.sql §2)
CREATE TYPE user_role AS ENUM (
  'propietario',
  'odontologo',
  'recepcion',
  'auxiliar',
  'especialista_externo'
);

-- Tabla de usuarios
CREATE TABLE users (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  email          CITEXT NOT NULL,
  password_hash  TEXT NOT NULL,
  full_name      TEXT NOT NULL,
  role           user_role NOT NULL,
  is_active      BOOLEAN NOT NULL DEFAULT true,
  last_login_at  TIMESTAMPTZ,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant_role ON users (tenant_id, role);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Row Level Security sobre users (docs/schema.sql §16)
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON users
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
