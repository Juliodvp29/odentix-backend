-- FASE2-03: Soporte de búsqueda de pacientes insensible a acentos y mayúsculas.
-- Habilita la extensión estándar unaccent de PostgreSQL y crea una función
-- IMMUTABLE para permitir indexación GIN con trigramas.

CREATE EXTENSION IF NOT EXISTS unaccent;

-- Wrapper IMMUTABLE sobre unaccent (la función estándar unaccent(text) es STABLE).
-- Necesaria para que PostgreSQL permita usarla en índices y expresiones indexadas.
CREATE OR REPLACE FUNCTION immutable_unaccent(text)
  RETURNS text AS $$
  SELECT public.unaccent('public.unaccent', $1);
$$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

-- Índice GIN trigram sobre el nombre completo normalizado (sin acentos y en minúsculas).
-- Optimiza búsquedas como "perez" encontrando "Pérez" o "MARIA" encontrando "María".
CREATE INDEX IF NOT EXISTS idx_patients_name_unaccent_trgm
  ON patients USING gin (immutable_unaccent(lower(first_name || ' ' || last_name)) gin_trgm_ops);
