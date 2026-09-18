-- FASE4-01: Modelar TreatmentPlan y TreatmentPlanItem.
-- Consistente con docs/schema.sql sección 10 (tablas treatment_plans y treatment_plan_items),
-- con dos consideraciones documentadas:
--   - procedure_id en treatment_plan_items se crea nullable SIN FK porque no
--     existe la tabla de procedimientos todavía (mismo precedente que Appointment
--     en FASE3-02 y WaitlistEntry en FASE3-06). La constraint FK se añadirá cuando
--     exista el catálogo.
--   - treatment_plan_items incluye updated_at y trigger set_updated_at() para
--     cumplir con el mapeo de TenantAwareEntity (mismo precedente que V8 y V9).

-- 1. Tipo enumerado para el ciclo de vida del plan de tratamiento
CREATE TYPE treatment_plan_status AS ENUM (
  'borrador',
  'presentado',
  'en_decision',
  'aceptado',
  'en_ejecucion',
  'completado',
  'rechazado',
  'pospuesto',
  'abandonado'
);

-- 2. Tabla treatment_plans
CREATE TABLE treatment_plans (
  id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id         UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id        UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id   UUID REFERENCES professionals(id) ON DELETE SET NULL,
  diagnosis         TEXT,
  status            treatment_plan_status NOT NULL DEFAULT 'borrador',
  total_price_cop   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (total_price_cop >= 0),
  presented_at      TIMESTAMPTZ,
  last_contact_at   TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices de consulta frecuente
CREATE INDEX idx_treatment_plans_patient ON treatment_plans (patient_id);
CREATE INDEX idx_treatment_plans_tenant_status ON treatment_plans (tenant_id, status);

-- Insumo directo de la Fase 9.1 del roadmap (regla "tratamiento sin seguimiento")
CREATE INDEX idx_treatment_plans_followup
  ON treatment_plans (tenant_id, status, last_contact_at)
  WHERE status IN ('presentado', 'en_decision');

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_treatment_plans_updated_at
  BEFORE UPDATE ON treatment_plans
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Consistencia de tenant entre el plan, el paciente y el profesional
CREATE OR REPLACE FUNCTION check_treatment_plan_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM patients p WHERE p.id = NEW.patient_id AND p.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'patient % no pertenece al tenant %', NEW.patient_id, NEW.tenant_id;
  END IF;

  IF NEW.professional_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM professionals pr
    WHERE pr.id = NEW.professional_id AND pr.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'professional % no pertenece al tenant %', NEW.professional_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_treatment_plan_tenant
  BEFORE INSERT OR UPDATE ON treatment_plans
  FOR EACH ROW EXECUTE FUNCTION check_treatment_plan_tenant_consistency();

COMMENT ON TABLE treatment_plans IS
  'Planes de tratamiento propuestos a pacientes (FASE4-01). Cada fila pertenece a un tenant.';

-- Row Level Security (segunda capa de defensa tras el filtro @TenantId)
ALTER TABLE treatment_plans ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON treatment_plans
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);

-- 3. Tabla treatment_plan_items
CREATE TABLE treatment_plan_items (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  treatment_plan_id  UUID NOT NULL REFERENCES treatment_plans(id) ON DELETE CASCADE,
  procedure_id       UUID,              -- FK pendiente hasta que exista el catálogo
  tooth_number       SMALLINT CHECK (tooth_number BETWEEN 11 AND 48), -- notación FDI
  price_cop          NUMERIC(12,2) NOT NULL CHECK (price_cop >= 0),
  discount_cop       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_cop >= 0),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índice para recuperar los ítems de un plan
CREATE INDEX idx_treatment_plan_items_plan ON treatment_plan_items (treatment_plan_id);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_treatment_plan_items_updated_at
  BEFORE UPDATE ON treatment_plan_items
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Consistencia de tenant entre el ítem y el plan de tratamiento padre
CREATE OR REPLACE FUNCTION check_treatment_plan_item_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM treatment_plans tp WHERE tp.id = NEW.treatment_plan_id AND tp.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'treatment_plan % no pertenece al tenant %', NEW.treatment_plan_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_treatment_plan_item_tenant
  BEFORE INSERT OR UPDATE ON treatment_plan_items
  FOR EACH ROW EXECUTE FUNCTION check_treatment_plan_item_tenant_consistency();

COMMENT ON TABLE treatment_plan_items IS
  'Procedimientos y piezas dentales incluidas en un plan de tratamiento (FASE4-01).';

-- Row Level Security
ALTER TABLE treatment_plan_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON treatment_plan_items
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
