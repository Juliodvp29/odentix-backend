-- FASE9-03: acciones sugeridas por oportunidad (opportunity_actions).
-- Consistente con docs/schema.sql sección 14, con las desviaciones
-- documentadas que ya son convención en este proyecto:
--   - updated_at añadido aunque schema.sql no lo trae: lo exige
--     TenantAwareEntity (precedente de V4, V8, V15, V18, V20, V22).
--   - channel TEXT nullable extra: schema.sql no lo trae, pero una acción
--     'enviar_mensaje' necesita saber por qué canal (email/WhatsApp) al
--     ejecutarse. Solo aplica a 'enviar_mensaje'; en 'crear_tarea' queda NULL.

CREATE TABLE opportunity_actions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id           UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
  opportunity_id      UUID NOT NULL REFERENCES opportunities(id) ON DELETE CASCADE,
  action_type         TEXT NOT NULL CHECK (action_type IN ('crear_tarea', 'enviar_mensaje')),
  channel             TEXT CHECK (channel IS NULL OR channel IN ('email', 'whatsapp', 'sms')),
  suggested_message   TEXT,
  executed            BOOLEAN NOT NULL DEFAULT false,
  executed_at         TIMESTAMPTZ,
  executed_by         UUID REFERENCES users(id) ON DELETE SET NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_opportunity_actions_opportunity ON opportunity_actions (opportunity_id);

CREATE TRIGGER trg_opportunity_actions_updated_at
  BEFORE UPDATE ON opportunity_actions
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE opportunity_actions IS
  'Acciones sugeridas por oportunidad (FASE9-03): crear_tarea o enviar_mensaje por un canal. Se ejecutan vía API.';


-- Row Level Security (mismo patrón que el resto de tablas de negocio,
-- docs/schema.sql §16). Segunda capa de defensa tras el filtro @TenantId.
ALTER TABLE opportunity_actions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON opportunity_actions
  USING (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL)
  WITH CHECK (tenant_id = current_tenant_id() OR current_tenant_id() IS NULL);
