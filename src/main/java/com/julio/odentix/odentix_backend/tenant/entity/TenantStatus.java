package com.julio.odentix.odentix_backend.tenant.entity;

/**
 * Estado de suscripción/cuenta de una clínica (tenant).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL `tenant_status` (docs/schema.sql §2):
 * 'trial', 'active', 'suspended', 'cancelled'.
 */
public enum TenantStatus {
  trial,
  active,
  suspended,
  cancelled
}
