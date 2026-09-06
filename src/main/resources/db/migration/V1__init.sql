-- FASE0-05: primera migración. A partir de aquí, todo cambio de esquema
-- pasa por una migración Flyway versionada (nunca ddl-auto: update).
--
-- Contiene infraestructura permanente (función set_updated_at(), copiada de
-- docs/schema.sql §3 — el trigger genérico que mantiene updated_at sin que
-- Java lo setee a mano) más una tabla mínima temporal para verificar el
-- mecanismo. migration_probe se eliminará cuando existan entidades reales
-- (Fase 1); la función queda.

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE migration_probe (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TRIGGER trg_set_updated_at
  BEFORE UPDATE ON migration_probe
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
