package com.julio.odentix.odentix_backend.saas.entity;

/**
 * Estados de un cobro del SaaS vía Bold (FASE11-04).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * {@code saas_payment_status} de `V26__create_saas_payments.sql`.
 */
public enum SaasPaymentStatus {
  pendiente,
  pagada,
  rechazada
}
