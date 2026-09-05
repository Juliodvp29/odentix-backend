-- =============================================================================
-- Sistema de Gestión y Productividad para Clínicas Odontológicas
-- schema.sql — Esquema completo de base de datos (PostgreSQL 16+)
-- =============================================================================
--
-- Corresponde a las Fases 0–9 y 11 del roadmap de backend.
-- Convenciones usadas en este esquema:
--   - snake_case en tablas y columnas, tablas en plural.
--   - Claves primarias UUID (gen_random_uuid()), excepto audit_log
--     (BIGSERIAL, por ser una tabla de solo-inserción de alto volumen).
--   - Todas las tablas de negocio (no catálogos globales) tienen tenant_id
--     NOT NULL, indexado, y con Row Level Security activado (ver sección 16).
--   - TIMESTAMPTZ en vez de TIMESTAMP en todas partes.
--   - Montos en pesos colombianos como NUMERIC(12,2) (columnas *_cop).
--   - updated_at se mantiene automáticamente por trigger (ver sección 15),
--     no hace falta setearlo manualmente desde la aplicación.
--
-- Cómo aplicar:
--   psql -U <usuario> -d <base_de_datos> -f schema.sql
--
-- Este archivo es un esquema de referencia consolidado. En el proyecto real
-- (Fase 0.5 del roadmap) se recomienda dividirlo en migraciones Flyway
-- incrementales (V1, V2, ...) siguiendo el mismo orden de secciones.
-- =============================================================================


-- =============================================================================
-- 1. EXTENSIONES
-- =============================================================================

-- UUIDs criptográficamente aleatorios (gen_random_uuid).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Email case-insensitive (evita duplicados tipo Juan@x.com vs juan@x.com).
CREATE EXTENSION IF NOT EXISTS citext;

-- Búsqueda de pacientes por nombre con similitud/ILIKE eficiente.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Necesaria para poder combinar columnas UUID/escalares con rangos en
-- restricciones EXCLUDE (se usa para evitar doble-agendamiento, sección 9).
CREATE EXTENSION IF NOT EXISTS btree_gist;


-- =============================================================================
-- 2. TIPOS ENUMERADOS
-- =============================================================================

CREATE TYPE tenant_status        AS ENUM ('trial', 'active', 'suspended', 'cancelled');
CREATE TYPE subscription_status  AS ENUM ('trialing', 'active', 'past_due', 'cancelled');
CREATE TYPE billing_cycle        AS ENUM ('monthly', 'annual');

CREATE TYPE user_role AS ENUM (
  'propietario',
  'odontologo',
  'recepcion',
  'auxiliar',
  'especialista_externo'
);

CREATE TYPE appointment_status AS ENUM (
  'programada', 'confirmada', 'atendida', 'no_show', 'cancelada'
);

CREATE TYPE risk_level AS ENUM ('bajo', 'medio', 'alto');

CREATE TYPE treatment_plan_status AS ENUM (
  'borrador', 'presentado', 'en_decision', 'aceptado',
  'en_ejecucion', 'completado', 'rechazado', 'pospuesto', 'abandonado'
);

CREATE TYPE invoice_status AS ENUM ('pendiente', 'parcial', 'pagada', 'anulada');
CREATE TYPE payment_method AS ENUM ('efectivo', 'tarjeta', 'transferencia', 'otro');
CREATE TYPE installment_status AS ENUM ('pendiente', 'pagada', 'vencida');

CREATE TYPE lead_status AS ENUM (
  'nuevo', 'contactado', 'calificado', 'cita_propuesta', 'cita_agendada',
  'cita_asistida', 'tratamiento_propuesto', 'tratamiento_aceptado', 'perdido'
);

CREATE TYPE waitlist_status AS ENUM ('activa', 'contactado', 'convertida', 'descartada');

CREATE TYPE task_status   AS ENUM ('pendiente', 'en_progreso', 'completada', 'cancelada');
CREATE TYPE task_priority AS ENUM ('baja', 'media', 'alta');

CREATE TYPE settlement_status AS ENUM ('pendiente', 'pagada');

CREATE TYPE odontogram_entry_type AS ENUM (
  'estado_actual', 'diagnostico', 'plan_propuesto', 'tratamiento_realizado'
);

CREATE TYPE notification_channel AS ENUM ('email', 'whatsapp', 'sms');
CREATE TYPE notification_status  AS ENUM ('pendiente', 'enviada', 'fallida');

CREATE TYPE opportunity_type AS ENUM (
  'lead_sin_respuesta', 'tratamiento_sin_seguimiento', 'cita_alto_riesgo',
  'espacio_disponible', 'paciente_inactivo', 'saldo_vencido', 'inventario_critico'
);
CREATE TYPE opportunity_status AS ENUM ('abierta', 'en_progreso', 'resuelta', 'descartada');

CREATE TYPE audit_action AS ENUM ('insert', 'update', 'delete', 'login_success', 'login_failed');


-- =============================================================================
-- 3. FUNCIONES DE UTILIDAD (usadas por triggers más abajo)
-- =============================================================================

-- Mantiene updated_at sin que la aplicación tenga que setearlo.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Tenant activo de la sesión/transacción actual, para Row Level Security.
-- La aplicación debe ejecutar, al inicio de cada transacción:
--   SELECT set_config('app.current_tenant_id', '<uuid-del-tenant>', true);
-- (En Spring, esto se hace en el mismo filtro/interceptor que puebla el
-- TenantContext descrito en la Fase 1.8 del roadmap — es la contraparte a
-- nivel de base de datos del filtro de aplicación de la Fase 1.9: dos capas
-- de aislamiento independientes, no una sustituye a la otra.)
CREATE OR REPLACE FUNCTION current_tenant_id()
RETURNS UUID AS $$
  SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid;
$$ LANGUAGE sql STABLE;


-- =============================================================================
-- 4. TENANTS, PLANES Y SUSCRIPCIONES
-- =============================================================================

CREATE TABLE tenants (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name        TEXT NOT NULL,
  tax_id      TEXT,                       -- NIT de la clínica
  status      tenant_status NOT NULL DEFAULT 'trial',
  timezone    TEXT NOT NULL DEFAULT 'America/Bogota',
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenants_status ON tenants (status);

COMMENT ON TABLE tenants IS 'Tabla raíz del modelo multi-tenant. Cada clínica cliente es un tenant.';

-- Row Level Security de tenants es un caso especial: se filtra por id,
-- no por tenant_id (no aplica al bloque dinámico de la sección 16).
ALTER TABLE tenants ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenants FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_self_isolation ON tenants
  USING (id = current_tenant_id());


CREATE TABLE plans (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  code               TEXT NOT NULL UNIQUE,   -- 'esencial' | 'profesional' | 'clinica'
  name               TEXT NOT NULL,
  monthly_price_cop  NUMERIC(12,2) NOT NULL CHECK (monthly_price_cop >= 0),
  annual_price_cop   NUMERIC(12,2) NOT NULL CHECK (annual_price_cop >= 0),
  is_active          BOOLEAN NOT NULL DEFAULT true,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE plans IS 'Catálogo global de planes (no es tenant-specific).';


-- Feature flags por plan: ¿el tenant tiene acceso al módulo o no?
CREATE TABLE plan_features (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id      UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  feature_key  TEXT NOT NULL,
  enabled      BOOLEAN NOT NULL DEFAULT false,
  UNIQUE (plan_id, feature_key)
);

COMMENT ON COLUMN plan_features.feature_key IS
  'Claves usadas por la app: crm_leads, cartera, automations_full, specialists, '
  'inventory, inventory_alerts, opportunities_engine, ai_assistant.';


-- Límites numéricos por plan: ¿cuántos puede tener/usar? NULL = ilimitado.
CREATE TABLE plan_limits (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  plan_id     UUID NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
  limit_key   TEXT NOT NULL,
  max_value   INTEGER,                      -- NULL = ilimitado
  UNIQUE (plan_id, limit_key)
);

COMMENT ON COLUMN plan_limits.limit_key IS
  'Claves usadas por la app: max_sedes, max_users, max_patients, '
  'whatsapp_conversations_month, max_specialists, opportunities_max_rules.';


CREATE TABLE tenant_subscriptions (
  id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id              UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  plan_id                UUID NOT NULL REFERENCES plans(id),
  status                 subscription_status NOT NULL DEFAULT 'trialing',
  billing_cycle          billing_cycle NOT NULL DEFAULT 'monthly',
  current_period_start   TIMESTAMPTZ NOT NULL DEFAULT now(),
  current_period_end     TIMESTAMPTZ NOT NULL,
  cancel_at_period_end   BOOLEAN NOT NULL DEFAULT false,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_subscriptions_tenant ON tenant_subscriptions (tenant_id);

-- Un tenant no puede tener dos suscripciones "vivas" al mismo tiempo.
CREATE UNIQUE INDEX uq_tenant_subscriptions_active
  ON tenant_subscriptions (tenant_id)
  WHERE status IN ('trialing', 'active', 'past_due');


-- =============================================================================
-- 5. USUARIOS Y AUDITORÍA
-- =============================================================================

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
  UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant_role ON users (tenant_id, role);

COMMENT ON TABLE users IS
  'Email único por tenant, no global: dos clínicas distintas pueden tener '
  'un usuario con el mismo email (ver Fase 1.2 del roadmap).';


CREATE TABLE audit_log (
  id           BIGSERIAL PRIMARY KEY,
  tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  user_id      UUID REFERENCES users(id) ON DELETE SET NULL,
  action       audit_action NOT NULL,
  entity_name  TEXT NOT NULL,
  entity_id    UUID,
  detail       JSONB,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_tenant_created ON audit_log (tenant_id, created_at DESC);
CREATE INDEX idx_audit_log_entity ON audit_log (tenant_id, entity_name, entity_id);

COMMENT ON TABLE audit_log IS
  'Tabla append-only. Nunca se actualiza ni se borra un registro existente.';


-- Función de auditoría genérica, reutilizada por varios triggers (sección 8).
CREATE OR REPLACE FUNCTION audit_sensitive_change()
RETURNS TRIGGER AS $$
DECLARE
  v_tenant_id UUID;
BEGIN
  v_tenant_id := COALESCE(NEW.tenant_id, OLD.tenant_id);

  INSERT INTO audit_log (tenant_id, action, entity_name, entity_id, detail)
  VALUES (
    v_tenant_id,
    lower(TG_OP)::audit_action,
    TG_TABLE_NAME,
    COALESCE(NEW.id, OLD.id),
    jsonb_build_object('old', to_jsonb(OLD), 'new', to_jsonb(NEW))
  );

  RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- 6. PROFESIONALES, ESPECIALISTAS Y CONSULTORIOS
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
-- Un usuario del sistema corresponde a lo sumo a un profesional.
CREATE UNIQUE INDEX uq_professionals_user ON professionals (user_id) WHERE user_id IS NOT NULL;


-- Extensión financiera de professionals para especialistas externos
-- (sección 8.13 del documento de arquitectura: honorarios y liquidaciones).
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

-- Un specialist SIEMPRE debe apuntar a un professional marcado is_external = true.
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

CREATE INDEX idx_settlements_specialist ON specialist_settlements (specialist_id, period_start);


CREATE TABLE rooms (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name        TEXT NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT true,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, name)
);


-- =============================================================================
-- 7. CATÁLOGO DE PROCEDIMIENTOS
-- =============================================================================

CREATE TABLE procedures (
  id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                 UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name                      TEXT NOT NULL,
  default_price_cop         NUMERIC(12,2),
  default_duration_minutes  INTEGER CHECK (default_duration_minutes > 0),
  is_active                 BOOLEAN NOT NULL DEFAULT true,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, name)
);


-- =============================================================================
-- 8. PACIENTES, HISTORIA CLÍNICA Y ODONTOGRAMA
-- =============================================================================

CREATE TABLE patients (
  id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id                 UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  document_type             TEXT,
  document_number           TEXT,
  first_name                TEXT NOT NULL,
  last_name                 TEXT NOT NULL,
  birth_date                DATE,
  phone                     TEXT,
  email                     CITEXT,
  address                   TEXT,
  emergency_contact_name    TEXT,
  emergency_contact_phone   TEXT,
  is_active                 BOOLEAN NOT NULL DEFAULT true,
  created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at                TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_tenant_active ON patients (tenant_id, is_active);

-- Documento único por tenant, pero solo cuando se registra (permite
-- pacientes sin documento capturado todavía, ej. un lead recién convertido).
CREATE UNIQUE INDEX uq_patients_tenant_document
  ON patients (tenant_id, document_number)
  WHERE document_number IS NOT NULL;

-- Búsqueda rápida de pacientes por nombre (usada por el buscador de la Fase 2.1).
CREATE INDEX idx_patients_name_trgm
  ON patients USING gin ((first_name || ' ' || last_name) gin_trgm_ops);

CREATE TRIGGER trg_audit_patients
AFTER INSERT OR UPDATE OR DELETE ON patients
FOR EACH ROW EXECUTE FUNCTION audit_sensitive_change();


CREATE TABLE patient_files (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id     UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  storage_key    TEXT NOT NULL,     -- referencia al objeto en S3/MinIO
  file_name      TEXT NOT NULL,
  content_type   TEXT,
  size_bytes     BIGINT CHECK (size_bytes >= 0),
  uploaded_by    UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_patient_files_patient ON patient_files (patient_id);


CREATE TABLE clinical_records (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id        UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id  UUID REFERENCES professionals(id) ON DELETE SET NULL,
  chief_complaint  TEXT,          -- motivo de consulta
  anamnesis        TEXT,
  diagnosis        TEXT,
  evolution        TEXT,
  recorded_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinical_records_patient ON clinical_records (patient_id, recorded_at DESC);

CREATE TRIGGER trg_audit_clinical_records
AFTER INSERT OR UPDATE OR DELETE ON clinical_records
FOR EACH ROW EXECUTE FUNCTION audit_sensitive_change();


-- Modelo de odontograma con separación explícita entre estado actual,
-- diagnóstico, plan propuesto y tratamiento realizado (sección 8.4 del
-- documento de arquitectura: "esto evita mezclar información clínica de
-- diferentes momentos").
CREATE TABLE odontogram_entries (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id     UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id    UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  tooth_number  SMALLINT NOT NULL CHECK (tooth_number BETWEEN 11 AND 48), -- notación FDI
  surface       TEXT,                                -- ej. 'oclusal', 'mesial'
  entry_type    odontogram_entry_type NOT NULL,
  condition     TEXT NOT NULL,                        -- ej. 'caries', 'obturado', 'ausente'
  recorded_by   UUID REFERENCES professionals(id) ON DELETE SET NULL,
  recorded_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes         TEXT
);

CREATE INDEX idx_odontogram_patient_tooth
  ON odontogram_entries (tenant_id, patient_id, tooth_number);


-- =============================================================================
-- 9. AGENDA, CITAS Y LISTA DE ESPERA
-- =============================================================================

CREATE TABLE appointments (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id             UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id            UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  professional_id       UUID REFERENCES professionals(id) ON DELETE SET NULL,
  room_id               UUID REFERENCES rooms(id) ON DELETE SET NULL,
  procedure_id          UUID REFERENCES procedures(id) ON DELETE SET NULL,
  starts_at             TIMESTAMPTZ NOT NULL,
  ends_at               TIMESTAMPTZ NOT NULL CHECK (ends_at > starts_at),
  estimated_value_cop   NUMERIC(12,2) DEFAULT 0,
  risk_level            risk_level NOT NULL DEFAULT 'bajo',
  status                appointment_status NOT NULL DEFAULT 'programada',
  notes                 TEXT,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  -- Columna generada, usada por la restricción EXCLUDE de más abajo.
  time_range TSTZRANGE GENERATED ALWAYS AS (tstzrange(starts_at, ends_at, '[)')) STORED
);

CREATE INDEX idx_appointments_tenant_date ON appointments (tenant_id, starts_at);
CREATE INDEX idx_appointments_patient ON appointments (patient_id);
CREATE INDEX idx_appointments_professional_date ON appointments (professional_id, starts_at);

-- Un mismo profesional no puede tener dos citas activas que se solapen.
-- Se excluyen 'cancelada' y 'no_show' porque esos horarios sí se pueden
-- reutilizar (ver Fase 3.4, recuperación de espacio).
ALTER TABLE appointments
  ADD CONSTRAINT no_overlapping_appointments
  EXCLUDE USING gist (
    professional_id WITH =,
    time_range WITH &&
  )
  WHERE (professional_id IS NOT NULL AND status NOT IN ('cancelada', 'no_show'));

-- Consistencia de tenant entre la cita y sus referencias (paciente y
-- profesional). Ver nota de la Fase 1.9 del roadmap: esto es defensa en
-- profundidad además del filtro de aplicación y de RLS, no un reemplazo.
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

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_appointment_tenant
BEFORE INSERT OR UPDATE ON appointments
FOR EACH ROW EXECUTE FUNCTION check_appointment_tenant_consistency();


CREATE TABLE waitlist_entries (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id     UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
  procedure_id   UUID REFERENCES procedures(id) ON DELETE SET NULL,
  desired_from   TIMESTAMPTZ,
  desired_to     TIMESTAMPTZ,
  status         waitlist_status NOT NULL DEFAULT 'activa',
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_waitlist_tenant_status ON waitlist_entries (tenant_id, status);


-- =============================================================================
-- 10. PLANES DE TRATAMIENTO Y FACTURACIÓN
-- =============================================================================

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

CREATE INDEX idx_treatment_plans_patient ON treatment_plans (patient_id);
-- Insumo directo de la Fase 9.1 del roadmap (regla "tratamiento sin seguimiento").
CREATE INDEX idx_treatment_plans_followup
  ON treatment_plans (tenant_id, status, last_contact_at)
  WHERE status IN ('presentado', 'en_decision');

CREATE OR REPLACE FUNCTION check_treatment_plan_tenant_consistency()
RETURNS TRIGGER AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM patients p WHERE p.id = NEW.patient_id AND p.tenant_id = NEW.tenant_id
  ) THEN
    RAISE EXCEPTION 'patient % no pertenece al tenant %', NEW.patient_id, NEW.tenant_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_treatment_plan_tenant
BEFORE INSERT OR UPDATE ON treatment_plans
FOR EACH ROW EXECUTE FUNCTION check_treatment_plan_tenant_consistency();


CREATE TABLE treatment_plan_items (
  id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id          UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  treatment_plan_id  UUID NOT NULL REFERENCES treatment_plans(id) ON DELETE CASCADE,
  procedure_id       UUID REFERENCES procedures(id) ON DELETE SET NULL,
  tooth_number       SMALLINT CHECK (tooth_number BETWEEN 11 AND 48),
  price_cop          NUMERIC(12,2) NOT NULL CHECK (price_cop >= 0),
  discount_cop       NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_cop >= 0),
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_treatment_plan_items_plan ON treatment_plan_items (treatment_plan_id);


CREATE TABLE invoices (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id          UUID NOT NULL REFERENCES patients(id) ON DELETE RESTRICT,
  treatment_plan_id   UUID REFERENCES treatment_plans(id) ON DELETE SET NULL,
  invoice_number      TEXT NOT NULL,
  status              invoice_status NOT NULL DEFAULT 'pendiente',
  subtotal_cop        NUMERIC(12,2) NOT NULL CHECK (subtotal_cop >= 0),
  discount_cop        NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_cop >= 0),
  total_cop           NUMERIC(12,2) NOT NULL CHECK (total_cop >= 0),
  issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, invoice_number)
);

CREATE INDEX idx_invoices_patient ON invoices (patient_id);
CREATE INDEX idx_invoices_tenant_status ON invoices (tenant_id, status);

CREATE TRIGGER trg_audit_invoices
AFTER INSERT OR UPDATE OR DELETE ON invoices
FOR EACH ROW EXECUTE FUNCTION audit_sensitive_change();


CREATE TABLE invoice_items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id     UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  description    TEXT NOT NULL,
  quantity       INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
  unit_price_cop NUMERIC(12,2) NOT NULL CHECK (unit_price_cop >= 0),
  total_cop      NUMERIC(12,2) GENERATED ALWAYS AS (quantity * unit_price_cop) STORED
);

CREATE INDEX idx_invoice_items_invoice ON invoice_items (invoice_id);


CREATE TABLE payments (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id    UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  amount_cop   NUMERIC(12,2) NOT NULL CHECK (amount_cop > 0),
  method       payment_method NOT NULL,
  reference    TEXT,
  paid_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);

CREATE TRIGGER trg_audit_payments
AFTER INSERT OR UPDATE OR DELETE ON payments
FOR EACH ROW EXECUTE FUNCTION audit_sensitive_change();


-- =============================================================================
-- 11. CARTERA Y PAGOS POR ETAPAS
-- =============================================================================

CREATE TABLE payment_plans (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  treatment_plan_id   UUID NOT NULL REFERENCES treatment_plans(id) ON DELETE CASCADE,
  total_amount_cop    NUMERIC(12,2) NOT NULL CHECK (total_amount_cop >= 0),
  installments_count  INTEGER NOT NULL CHECK (installments_count > 0),
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_plans_treatment_plan ON payment_plans (treatment_plan_id);


CREATE TABLE installments (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  payment_plan_id     UUID NOT NULL REFERENCES payment_plans(id) ON DELETE CASCADE,
  installment_number  INTEGER NOT NULL CHECK (installment_number > 0),
  amount_cop          NUMERIC(12,2) NOT NULL CHECK (amount_cop > 0),
  due_date            DATE NOT NULL,
  status              installment_status NOT NULL DEFAULT 'pendiente',
  paid_at             TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (payment_plan_id, installment_number)
);

-- Insumo directo del dashboard de cartera (sección 8.12 del doc de arquitectura).
CREATE INDEX idx_installments_tenant_status_due
  ON installments (tenant_id, status, due_date);

-- Se ejecuta periódicamente (ej. job diario de Spring @Scheduled, Fase 6.2
-- del roadmap) para marcar cuotas vencidas. No es un trigger porque el
-- vencimiento depende del paso del tiempo, no de un evento de escritura.
CREATE OR REPLACE FUNCTION mark_overdue_installments()
RETURNS INTEGER AS $$
DECLARE
  v_affected INTEGER;
BEGIN
  UPDATE installments
     SET status = 'vencida'
   WHERE status = 'pendiente'
     AND due_date < CURRENT_DATE;

  GET DIAGNOSTICS v_affected = ROW_COUNT;
  RETURN v_affected;
END;
$$ LANGUAGE plpgsql;


-- =============================================================================
-- 12. CRM DE LEADS
-- =============================================================================

CREATE TABLE leads (
  id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id               UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  full_name               TEXT NOT NULL,
  phone                   TEXT,
  email                   CITEXT,
  source                  TEXT,             -- 'meta_ads', 'google_ads', 'instagram', etc.
  campaign                TEXT,
  procedure_of_interest   TEXT,
  estimated_value_cop     NUMERIC(12,2),
  status                  lead_status NOT NULL DEFAULT 'nuevo',
  assigned_to             UUID REFERENCES users(id) ON DELETE SET NULL,
  converted_patient_id    UUID REFERENCES patients(id) ON DELETE SET NULL,
  last_contact_at         TIMESTAMPTZ,
  next_action_at          TIMESTAMPTZ,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_leads_tenant_status ON leads (tenant_id, status);
CREATE INDEX idx_leads_tenant_source ON leads (tenant_id, source);
-- Insumo directo de la Fase 9.1 del roadmap (regla "lead sin respuesta").
CREATE INDEX idx_leads_unresponded
  ON leads (tenant_id, status, created_at)
  WHERE status = 'nuevo';


CREATE TABLE lead_activities (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  lead_id        UUID NOT NULL REFERENCES leads(id) ON DELETE CASCADE,
  user_id        UUID REFERENCES users(id) ON DELETE SET NULL,
  activity_type  TEXT NOT NULL,    -- 'llamada', 'whatsapp', 'email', 'nota'
  notes          TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_lead_activities_lead ON lead_activities (lead_id, created_at DESC);


-- =============================================================================
-- 13. INVENTARIO
-- =============================================================================

CREATE TABLE inventory_items (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  name           TEXT NOT NULL,
  unit           TEXT,
  quantity       INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
  min_threshold  INTEGER NOT NULL DEFAULT 0 CHECK (min_threshold >= 0),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (tenant_id, name)
);

-- Insumo directo de la Fase 9.1 del roadmap (regla "inventario crítico").
CREATE INDEX idx_inventory_items_critical
  ON inventory_items (tenant_id)
  WHERE quantity <= min_threshold;


CREATE TABLE stock_movements (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  inventory_item_id   UUID NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
  quantity_delta      INTEGER NOT NULL CHECK (quantity_delta <> 0), -- + entrada, - salida
  reason              TEXT,
  created_by          UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_stock_movements_item ON stock_movements (inventory_item_id, created_at DESC);

-- Aplica el movimiento al stock automáticamente. Si el resultado deja el
-- stock en negativo, el CHECK de inventory_items.quantity revierte toda
-- la transacción (no hace falta validar "stock suficiente" a mano).
CREATE OR REPLACE FUNCTION apply_stock_movement()
RETURNS TRIGGER AS $$
BEGIN
  UPDATE inventory_items
     SET quantity = quantity + NEW.quantity_delta
   WHERE id = NEW.inventory_item_id
     AND tenant_id = NEW.tenant_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'inventory_item % no encontrado para el tenant %',
      NEW.inventory_item_id, NEW.tenant_id;
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_apply_stock_movement
AFTER INSERT ON stock_movements
FOR EACH ROW EXECUTE FUNCTION apply_stock_movement();


-- =============================================================================
-- 14. TAREAS, NOTIFICACIONES, OPORTUNIDADES
-- =============================================================================

-- related_entity_type/id es una referencia polimórfica intencional (puede
-- apuntar a leads, treatment_plans, appointments, etc.). No lleva FK real
-- porque no hay una única tabla padre; la integridad se garantiza desde la
-- capa de aplicación. Es la única relación de este esquema que rompe esa
-- regla, y se hace a propósito para no forzar una jerarquía artificial.
CREATE TABLE tasks (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id            UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  title                TEXT NOT NULL,
  description          TEXT,
  related_entity_type  TEXT,
  related_entity_id    UUID,
  assigned_to          UUID REFERENCES users(id) ON DELETE SET NULL,
  due_at               TIMESTAMPTZ,
  priority             task_priority NOT NULL DEFAULT 'media',
  status               task_status NOT NULL DEFAULT 'pendiente',
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tasks_tenant_assignee_status ON tasks (tenant_id, assigned_to, status);
CREATE INDEX idx_tasks_related_entity ON tasks (related_entity_type, related_entity_id);


CREATE TABLE notifications (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id      UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  patient_id     UUID REFERENCES patients(id) ON DELETE SET NULL,
  channel        notification_channel NOT NULL,
  recipient      TEXT NOT NULL,
  template_key   TEXT,
  payload        JSONB,
  status         notification_status NOT NULL DEFAULT 'pendiente',
  sent_at        TIMESTAMPTZ,
  error_detail   TEXT,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_tenant_status ON notifications (tenant_id, status);

COMMENT ON TABLE notifications IS
  'El fallo de un canal (ej. WhatsApp caído) se registra aquí con status '
  'fallida y error_detail; nunca debe bloquear el flujo que la originó '
  '(ver principio de resiliencia de la Fase 8.3 y 10.3 del roadmap).';


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

CREATE INDEX idx_opportunities_tenant_status_priority
  ON opportunities (tenant_id, status, priority DESC);


CREATE TABLE opportunity_actions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  opportunity_id      UUID NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
  action_type         TEXT NOT NULL,   -- 'enviar_mensaje', 'crear_tarea', etc.
  suggested_message   TEXT,
  executed            BOOLEAN NOT NULL DEFAULT false,
  executed_at         TIMESTAMPTZ,
  executed_by         UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_opportunity_actions_opportunity ON opportunity_actions (opportunity_id);


-- =============================================================================
-- 15. TRIGGERS AUTOMÁTICOS DE updated_at
-- =============================================================================
-- En vez de repetir "CREATE TRIGGER ..." a mano para cada una de las ~25
-- tablas que tienen updated_at, se recorre el catálogo del esquema y se
-- crea el trigger dinámicamente. Si en el futuro se agrega una tabla nueva
-- con columna updated_at, solo hay que volver a correr este bloque.

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.table_name
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.column_name = 'updated_at'
  LOOP
    EXECUTE format(
      'DROP TRIGGER IF EXISTS trg_set_updated_at ON %I;', r.table_name
    );
    EXECUTE format(
      'CREATE TRIGGER trg_set_updated_at
         BEFORE UPDATE ON %I
         FOR EACH ROW EXECUTE FUNCTION set_updated_at();',
      r.table_name
    );
  END LOOP;
END;
$$;


-- =============================================================================
-- 16. ROW LEVEL SECURITY (aislamiento multi-tenant a nivel de base de datos)
-- =============================================================================
-- Esto es la contraparte a nivel de PostgreSQL del filtro de tenant_id de
-- la Fase 1.9 del roadmap (implementado con Hibernate Filters en el
-- backend). Son dos capas independientes de defensa: un bug en el filtro
-- de Hibernate no basta por sí solo para filtrar datos de otro tenant si
-- RLS está activo, y viceversa.
--
-- Requisito operativo: el rol de base de datos que use la aplicación en
-- producción NO debe tener el atributo BYPASSRLS, y no debería ser el
-- dueño (owner) de las tablas — se recomienda un rol `app_user` separado
-- del rol `app_admin`/migraciones que sí puede necesitar bypass para
-- tareas administrativas (crear tenants nuevos, reportes cross-tenant).
-- Ver Fase 12.1 del roadmap.

DO $$
DECLARE
  r RECORD;
BEGIN
  FOR r IN
    SELECT c.table_name
    FROM information_schema.columns c
    WHERE c.table_schema = 'public'
      AND c.column_name = 'tenant_id'
  LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', r.table_name);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY;', r.table_name);
    EXECUTE format(
      'DROP POLICY IF EXISTS tenant_isolation ON %I;', r.table_name
    );
    EXECUTE format(
      'CREATE POLICY tenant_isolation ON %I
         USING (tenant_id = current_tenant_id())
         WITH CHECK (tenant_id = current_tenant_id());',
      r.table_name
    );
  END LOOP;
END;
$$;


-- =============================================================================
-- 17. DATOS SEMILLA — Planes, features y límites
-- =============================================================================
-- Corresponde a la tabla de pricing del roadmap de backend. El precio anual
-- equivale a "10 meses" (~2 meses gratis), un descuento estándar de mercado
-- fácil de comunicar comercialmente.

INSERT INTO plans (code, name, monthly_price_cop, annual_price_cop) VALUES
  ('esencial',    'Esencial',    99900.00,  999000.00),
  ('profesional', 'Profesional', 169900.00, 1699000.00),
  ('clinica',     'Clínica',     259900.00, 2599000.00);

-- --- Feature flags ---
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_features (plan_id, feature_key, enabled)
SELECT p.id, f.feature_key, f.enabled FROM (
  VALUES
    -- Esencial: todo apagado salvo lo mínimo del flujo clínico/facturación,
    -- que no necesita feature flag porque está disponible en todos los planes.
    ('esencial',    'crm_leads',            false),
    ('esencial',    'cartera',              false),
    ('esencial',    'automations_full',     false),
    ('esencial',    'specialists',          false),
    ('esencial',    'inventory',            false),
    ('esencial',    'inventory_alerts',     false),
    ('esencial',    'opportunities_engine', false),
    ('esencial',    'ai_assistant',         false),

    -- Profesional: CRM, cartera, automatizaciones completas, especialistas
    -- e inventario básico habilitados; motor de oportunidades limitado
    -- (ver plan_limits.opportunities_max_rules); sin IA todavía.
    ('profesional', 'crm_leads',            true),
    ('profesional', 'cartera',              true),
    ('profesional', 'automations_full',     true),
    ('profesional', 'specialists',          true),
    ('profesional', 'inventory',            true),
    ('profesional', 'inventory_alerts',     false),
    ('profesional', 'opportunities_engine', true),
    ('profesional', 'ai_assistant',         false),

    -- Clínica: todo habilitado, sin excepciones.
    ('clinica',     'crm_leads',            true),
    ('clinica',     'cartera',              true),
    ('clinica',     'automations_full',     true),
    ('clinica',     'specialists',          true),
    ('clinica',     'inventory',            true),
    ('clinica',     'inventory_alerts',     true),
    ('clinica',     'opportunities_engine', true),
    ('clinica',     'ai_assistant',         true)
) AS f(plan_code, feature_key, enabled)
JOIN p ON p.code = f.plan_code;

-- --- Límites numéricos (NULL = ilimitado) ---
WITH p AS (SELECT id, code FROM plans)
INSERT INTO plan_limits (plan_id, limit_key, max_value)
SELECT p.id, l.limit_key, l.max_value FROM (
  VALUES
    ('esencial',    'max_sedes',                       1),
    ('esencial',    'max_users',                       2),
    ('esencial',    'max_patients',                    150),
    ('esencial',    'whatsapp_conversations_month',    0),
    ('esencial',    'max_specialists',                 0),
    ('esencial',    'opportunities_max_rules',         0),

    ('profesional', 'max_sedes',                       2),
    ('profesional', 'max_users',                       6),
    ('profesional', 'max_patients',                    800),
    ('profesional', 'whatsapp_conversations_month',    300),
    ('profesional', 'max_specialists',                 2),
    ('profesional', 'opportunities_max_rules',         2),

    ('clinica',     'max_sedes',                       NULL),
    ('clinica',     'max_users',                       NULL),
    ('clinica',     'max_patients',                    NULL),
    ('clinica',     'whatsapp_conversations_month',    1000),
    ('clinica',     'max_specialists',                 NULL),
    ('clinica',     'opportunities_max_rules',         NULL)
) AS l(plan_code, limit_key, max_value)
JOIN p ON p.code = l.plan_code;


-- =============================================================================
-- Fin de schema.sql
-- =============================================================================
