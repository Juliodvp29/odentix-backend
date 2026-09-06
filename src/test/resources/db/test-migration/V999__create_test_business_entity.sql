-- Migración exclusiva para pruebas de integración (FASE1-09 / FASE1-10).
-- Crea una tabla de negocio de prueba que sigue estrictamente la convención
-- de ARCHITECTURE.md: tenant_id NOT NULL REFERENCES tenants(id), índice, RLS.

CREATE TABLE test_business_entities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_test_business_entities_tenant_id ON test_business_entities(tenant_id);

ALTER TABLE test_business_entities ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_business_entities FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON test_business_entities
    FOR ALL
    USING (tenant_id = current_tenant_id())
    WITH CHECK (tenant_id = current_tenant_id());
