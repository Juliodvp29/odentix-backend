ALTER TABLE waitlist_entries
  ADD COLUMN contacted_at TIMESTAMPTZ,
  ADD COLUMN converted_at TIMESTAMPTZ,
  ADD COLUMN discarded_at TIMESTAMPTZ,
  ADD COLUMN discard_reason TEXT,
  ADD COLUMN converted_appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
  ADD COLUMN recovered_from_appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL;

CREATE INDEX idx_waitlist_tenant_created_id
  ON waitlist_entries (tenant_id, created_at DESC, id ASC);

CREATE UNIQUE INDEX uq_waitlist_converted_appointment
  ON waitlist_entries (converted_appointment_id)
  WHERE converted_appointment_id IS NOT NULL;

CREATE UNIQUE INDEX uq_waitlist_recovered_from_appointment
  ON waitlist_entries (recovered_from_appointment_id)
  WHERE recovered_from_appointment_id IS NOT NULL;

ALTER TABLE waitlist_entries
  ADD CONSTRAINT ck_waitlist_converted_at
    CHECK (converted_at IS NULL OR status = 'convertida'),
  ADD CONSTRAINT ck_waitlist_converted_appointment
    CHECK (converted_appointment_id IS NULL OR status = 'convertida'),
  ADD CONSTRAINT ck_waitlist_recovered_from_appointment
    CHECK (recovered_from_appointment_id IS NULL OR status = 'convertida'),
  ADD CONSTRAINT ck_waitlist_discarded_at
    CHECK (discarded_at IS NULL OR status = 'descartada'),
  ADD CONSTRAINT ck_waitlist_discard_reason
    CHECK (discard_reason IS NULL OR status = 'descartada'),
  ADD CONSTRAINT ck_waitlist_distinct_appointments
    CHECK (
      converted_appointment_id IS NULL
      OR recovered_from_appointment_id IS NULL
      OR converted_appointment_id <> recovered_from_appointment_id
    );

CREATE OR REPLACE FUNCTION check_waitlist_entry_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM patients p
    WHERE p.id = NEW.patient_id AND p.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'patient % no pertenece al tenant %', NEW.patient_id, NEW.tenant_id;
  END IF;

  IF NEW.converted_appointment_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM appointments a
    WHERE a.id = NEW.converted_appointment_id
      AND a.tenant_id = NEW.tenant_id
      AND a.patient_id = NEW.patient_id
  ) THEN
    RAISE EXCEPTION 'converted appointment % no pertenece al tenant o paciente %',
      NEW.converted_appointment_id, NEW.patient_id;
  END IF;

  IF NEW.recovered_from_appointment_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM appointments a
    WHERE a.id = NEW.recovered_from_appointment_id
      AND a.tenant_id = NEW.tenant_id
      AND a.status = 'cancelada'
  ) THEN
    RAISE EXCEPTION 'source appointment % is not a cancelled appointment in tenant %',
      NEW.recovered_from_appointment_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_waitlist_entry_tenant
BEFORE INSERT OR UPDATE ON waitlist_entries
FOR EACH ROW EXECUTE FUNCTION check_waitlist_entry_tenant_consistency();

ALTER TABLE waitlist_entries ENABLE ROW LEVEL SECURITY;
