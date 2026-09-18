-- FASE7-01: especialistas externos y liquidaciones (specialists, specialist_settlements).
-- Consistente con docs/schema.sql sección 6 (extensión financiera de professionals,
-- sección 8.13 del documento de arquitectura), con las mismas desviaciones
-- documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15, V18).
--   - created_at/updated_at añadidos donde los exige TenantAwareEntity
--     (precedente de V4, V8, V15, V18).
--
-- Defensa en profundidad para "specialist solo sobre professional externo":
-- el trigger trg_check_specialist_is_external es el mecanismo autoritativo
-- en BD; la entidad Java Specialist repite la validación en @PrePersist/
-- @PreUpdate (igual que multi-tenancy: dos capas independientes, ninguna
-- sustituye a la otra).

-- Estado de una liquidación: pendiente → pagada (el cálculo del monto se
-- implementa en FASE7-02; aquí solo el modelo).
CREATE TYPE settlement_status AS ENUM (
  'pendiente',
  'pagada'
);

-- =============================================================================
-- 1. TABLA SPECIALISTS (1—1 con professionals)
-- =============================================================================
CREATE TABLE specialists (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  professional_id   UUID NOT NULL UNIQUE REFERENCES professionals(id) ON DELETE CASCADE,
  fee_percentage    NUMERIC(5,2) NOT NULL CHECK (fee_percentage BETWEEN 0 AND 100),
  payment_terms     TEXT,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_specialists_tenant ON specialists (tenant_id);

CREATE TRIGGER trg_specialists_updated_at
  BEFORE UPDATE ON specialists
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE specialists IS
  'Extensión financiera de professionals para especialistas externos (FASE7-01). Solo admite professionals con is_external = true.';

-- Un specialist SIEMPRE debe apuntar a un professional marcado is_external = true
-- (reutilizado tal cual de docs/schema.sql — no reimplementado, es el mismo trigger).
CREATE OR REPLACE FUNCTION check_specialist_is_external()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM professionals p
    WHERE p.id = NEW.professional_id AND p.is_external = true
  ) THEN
    RAISE EXCEPTION
      'professional % debe tener is_external = true para tener un registro en specialists',
      NEW.professional_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_specialist_is_external
BEFORE INSERT OR UPDATE ON specialists
FOR EACH ROW EXECUTE FUNCTION check_specialist_is_external();

-- =============================================================================
-- 2. TABLA SPECIALIST_SETTLEMENTS (liquidación por periodo)
-- =============================================================================
CREATE TABLE specialist_settlements (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  specialist_id          UUID NOT NULL REFERENCES specialists(id) ON DELETE CASCADE,
  period_start           DATE NOT NULL,
  period_end             DATE NOT NULL CHECK (period_end >= period_start),
  gross_production_cop   NUMERIC(12,2) NOT NULL DEFAULT 0,
  fee_amount_cop         NUMERIC(12,2) NOT NULL DEFAULT 0,
  status                 settlement_status NOT NULL DEFAULT 'pendiente',
  paid_at                TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_settlements_specialist
  ON specialist_settlements (specialist_id, period_start);

CREATE TRIGGER trg_specialist_settlements_updated_at
  BEFORE UPDATE ON specialist_settlements
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE specialist_settlements IS
  'Liquidaciones por periodo de un especialista externo (FASE7-01). El cálculo de producción bruta llega en FASE7-02.';


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16, precedente de V11 y V18). Segunda capa de defensa
-- tras el filtro @TenantId de Hibernate.
ALTER TABLE specialists ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON specialists
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

ALTER TABLE specialist_settlements ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON specialist_settlements
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
