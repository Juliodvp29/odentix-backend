-- FASE9-01: Motor de oportunidades — tabla opportunities.
-- Consistente con docs/schema.sql sección 14 (tabla opportunities), con las
-- mismas desviaciones documentadas que el resto de tablas de negocio:
--   - No se crean triggers de auditoría: la auditoría se gestiona desde
--     Java vía AuditService (precedente de V5, V7, V8, V15, V18–V22).
--
-- La referencia polimórfica related_entity_type/id es intencional y NO lleva
-- FK real (puede apuntar a treatment_plans, leads, appointments, etc.; ver
-- nota en schema.sql §14 y precedente de tasks en V21): la integridad se
-- garantiza desde la aplicación.

-- 1. Tipos enumerados para oportunidades
CREATE TYPE opportunity_type AS ENUM (
  'lead_sin_respuesta',
  'tratamiento_sin_seguimiento',
  'cita_alto_riesgo',
  'espacio_disponible',
  'paciente_inactivo',
  'saldo_vencido',
  'inventario_critico'
);

CREATE TYPE opportunity_status AS ENUM (
  'abierta',
  'en_progreso',
  'resuelta',
  'descartada'
);

-- 2. Tabla opportunities
CREATE TABLE opportunities (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  type                  opportunity_type NOT NULL,
  related_entity_type   TEXT,
  related_entity_id     UUID,
  estimated_value_cop   NUMERIC(12,2) DEFAULT 0,
  priority              SMALLINT NOT NULL DEFAULT 1 CHECK (priority BETWEEN 1 AND 5),
  status                opportunity_status NOT NULL DEFAULT 'abierta',
  detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at           TIMESTAMPTZ,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice principal: bandeja de oportunidades del tenant ordenada por prioridad
CREATE INDEX idx_opportunities_tenant_status_priority
  ON opportunities (tenant_id, status, priority DESC);

-- Índice para idempotencia: buscar oportunidad abierta por entidad relacionada
CREATE INDEX idx_opportunities_related_entity
  ON opportunities (related_entity_type, related_entity_id);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_opportunities_updated_at
  BEFORE UPDATE ON opportunities
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE opportunities IS
  'Oportunidades de negocio detectadas automáticamente por el motor de reglas '
  '(FASE9-01). related_entity es polimórfico intencional, sin FK. Cada regla '
  '(tratamiento sin seguimiento, lead sin respuesta, etc.) genera entradas que '
  'la clínica puede actuar o descartar.';

-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE opportunities ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON opportunities
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
