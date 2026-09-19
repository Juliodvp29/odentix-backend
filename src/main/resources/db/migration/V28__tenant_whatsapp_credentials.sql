-- Credenciales WhatsApp por clínica (pre-Fase 12: cada clínica envía desde su
-- propio número; un número global confunde a los pacientes y las respuestas
-- llegarían al número equivocado).
-- El token se guarda CIFRADO (base64 de IV + AES-GCM), nunca en claro: ver
-- DataEncryptionService. La llave vive en DATA_ENCRYPTION_KEY (env, Render).

ALTER TABLE tenants
  ADD COLUMN whatsapp_phone_number_id TEXT,
  ADD COLUMN whatsapp_token_cifrado TEXT;

COMMENT ON COLUMN tenants.whatsapp_token_cifrado IS
  'Token de Meta cifrado con AES-GCM. Jamás en claro ni en respuestas API.';
