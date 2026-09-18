-- FASE5-01: Modelar Lead y su pipeline comercial (Lead y LeadActivity).
-- Consistente con docs/schema.sql sección 12 (tablas leads y lead_activities),
-- con updated_at en lead_activities para adherencia completa a TenantAwareEntity.

-- 1. Tipo enumerado para el ciclo de vida comercial del lead (pipeline de 9 estados)
CREATE TYPE lead_status AS ENUM (
  'nuevo',
  'contactado',
  'calificado',
  'cita_propuesta',
  'cita_agendada',
  'cita_asistida',
  'tratamiento_propuesto',
  'tratamiento_aceptado',
  'perdido'
);

-- 2. Tabla leads
CREATE TABLE leads (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  full_name               TEXT NOT NULL,
  phone                   TEXT,
  email                   CITEXT,
  source                  TEXT,             -- 'meta_ads', 'google_ads', 'instagram', 'referido', etc.
  campaign                TEXT,
  procedure_of_interest   TEXT,
  estimated_value_cop     NUMERIC(12,2) CHECK (estimated_value_cop IS NULL OR estimated_value_cop >= 0),
  status                  lead_status NOT NULL DEFAULT 'nuevo',
  assigned_to             UUID REFERENCES users(id) ON DELETE SET NULL,
  converted_patient_id    UUID REFERENCES patients(id) ON DELETE SET NULL,
  last_contact_at         TIMESTAMPTZ,
  next_action_at          TIMESTAMPTZ,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Índices operativos y analíticos
CREATE INDEX idx_leads_tenant_status ON leads (tenant_id, status);
CREATE INDEX idx_leads_tenant_source ON leads (tenant_id, source);
CREATE INDEX idx_leads_assigned_to ON leads (assigned_to);
CREATE INDEX idx_leads_converted_patient ON leads (converted_patient_id);

-- Insumo directo de la Fase 9.1 del roadmap (regla "lead sin respuesta")
CREATE INDEX idx_leads_unresponded
  ON leads (tenant_id, status, created_at)
  WHERE status = 'nuevo';

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_leads_updated_at
  BEFORE UPDATE ON leads
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Consistencia multi-tenant entre el lead, el usuario asignado y el paciente convertido
CREATE OR REPLACE FUNCTION check_lead_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NEW.assigned_to IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.id = NEW.assigned_to AND u.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'user % no pertenece al tenant %', NEW.assigned_to, NEW.tenant_id;
  END IF;

  IF NEW.converted_patient_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM patients p WHERE p.id = NEW.converted_patient_id AND p.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'patient % no pertenece al tenant %', NEW.converted_patient_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_lead_tenant
  BEFORE INSERT OR UPDATE ON leads
  FOR EACH ROW EXECUTE FUNCTION check_lead_tenant_consistency();

COMMENT ON TABLE leads IS
  'Prospectos/leads comerciales de la clínica (FASE5-01). Cada fila pertenece a un tenant.';

-- Row Level Security (segunda capa de defensa tras el filtro @TenantId)
ALTER TABLE leads ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON leads
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);


-- 3. Tabla lead_activities (historial de interacciones comerciales)
CREATE TABLE lead_activities (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  lead_id        UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
  user_id        UUID REFERENCES users(id) ON DELETE SET NULL,
  activity_type  TEXT NOT NULL CHECK (activity_type IN ('llamada', 'whatsapp', 'email', 'nota')),
  notes          TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_activities_lead ON lead_activities (lead_id, created_at DESC);
CREATE INDEX idx_lead_activities_tenant ON lead_activities (tenant_id);

CREATE TRIGGER trg_lead_activities_updated_at
  BEFORE UPDATE ON lead_activities
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Consistencia multi-tenant para las actividades de contacto
CREATE OR REPLACE FUNCTION check_lead_activity_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM leads l WHERE l.id = NEW.lead_id AND l.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'lead % no pertenece al tenant %', NEW.lead_id, NEW.tenant_id;
  END IF;

  IF NEW.user_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM users u WHERE u.id = NEW.user_id AND u.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'user % no pertenece al tenant %', NEW.user_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_lead_activity_tenant
  BEFORE INSERT OR UPDATE ON lead_activities
  FOR EACH ROW EXECUTE FUNCTION check_lead_activity_tenant_consistency();

COMMENT ON TABLE lead_activities IS
  'Historial de contactos e interacciones con un prospecto (FASE5-01). En cascada con leads.';

ALTER TABLE lead_activities ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON lead_activities
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
