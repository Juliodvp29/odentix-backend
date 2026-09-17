-- FASE3-02: Modelar Appointment y evitar doble-agendamiento con EXCLUDE USING gist.
-- Consistente con docs/schema.sql sección 9 (tabla appointments).

-- 1. Extensión btree_gist para poder usar '=' sobre UUID en restricciones EXCLUDE
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- 2. Tipos enumerados de cita y nivel de riesgo
CREATE TYPE appointment_status AS ENUM (
  'programada',
  'confirmada',
  'atendida',
  'no_show',
  'cancelada'
);

CREATE TYPE risk_level AS ENUM (
  'bajo',
  'medio',
  'alto'
);

-- 3. Tabla appointments
CREATE TABLE appointments (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id            UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id       UUID REFERENCES professionals(id) ON DELETE SET NULL,
  room_id               UUID REFERENCES rooms(id) ON DELETE SET NULL,
  procedure_id          UUID,
  starts_at             TIMESTAMPTZ NOT NULL,
  ends_at               TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
  estimated_value_cop   NUMERIC(12,2) DEFAULT 0,
  risk_level            risk_level NOT NULL DEFAULT 'bajo',
  status                appointment_status NOT NULL DEFAULT 'programada',
  notes                 TEXT,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Columna generada para indexación y cálculo de rangos temporales en GiST
  time_range            TSTZRANGE GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED
);

CREATE INDEX idx_appointments_tenant_date ON appointments (tenant_id, starts_at);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);
CREATE INDEX idx_appointments_professional_date ON appointments (professional_id, starts_at);

-- Trigger para mantener updated_at automáticamente
CREATE TRIGGER trg_appointments_updated_at
  BEFORE UPDATE ON appointments
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 4. Restricción de exclusión para evitar citas solapadas del mismo profesional.
-- Se excluyen 'cancelada' y 'no_show' para liberar el espacio horario.
ALTER TABLE appointments
  ADD CONSTRAINT no_overlapping_appointments
  EXCLUDE USING gist (
    professional_id WITH =,
    time_range WITH &&
  )
  WHERE (professional_id IS NOT NULL AND status NOT IN ('cancelada', 'no_show'));

-- 5. Consistencia de tenant entre la cita, el paciente, el profesional y el consultorio
CREATE OR REPLACE FUNCTION check_appointment_tenant_consistency()
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

  IF NEW.room_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM rooms r
    WHERE r.id = NEW.room_id AND r.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'room % no pertenece al tenant %', NEW.room_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_appointment_tenant
BEFORE INSERT OR UPDATE ON appointments
FOR EACH ROW EXECUTE FUNCTION check_appointment_tenant_consistency();

COMMENT ON TABLE appointments IS
  'Citas odontológicas (FASE3-02). Evita solapamiento por profesional mediante EXCLUDE USING gist.';

-- 6. Row Level Security
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON appointments
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
