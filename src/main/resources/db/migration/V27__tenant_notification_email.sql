-- Remitente de email por clínica (pre-Fase 12, a pedido: un remitente global
-- no sirve en multi-tenant — cada clínica responde con su propia dirección).
-- Sin RLS: tenants es la raíz (ya tiene su política propia desde V2).

ALTER TABLE tenants
  ADD COLUMN notification_email TEXT,
  ADD COLUMN notification_name TEXT;

COMMENT ON COLUMN tenants.notification_email IS
  'Remitente de los correos de la clínica. Sin valor se usa el global NOTIFICATIONS_EMAIL_FROM.';
