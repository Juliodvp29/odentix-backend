-- FASE4-04: secuencia para numerar facturas (FAC-000001, ...).
--
-- La numeración secuencial y sin carreras no se puede hacer bien con un
-- MAX()+1 en Java (dos requests concurrentes generarían el mismo número).
-- La secuencia es global (no por tenant): como nunca se repite, la unicidad
-- por tenant exigida por uq_invoices_tenant_number queda garantizada.
-- Los huecos por rollback son aceptables antes de la facturación
-- electrónica (integración futura, fuera de alcance).

CREATE SEQUENCE IF NOT EXISTS invoice_number_seq START WITH 1 INCREMENT BY 1;
