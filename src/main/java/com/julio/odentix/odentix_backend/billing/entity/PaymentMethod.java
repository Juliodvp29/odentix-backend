package com.julio.odentix.odentix_backend.billing.entity;

/**
 * Medios de pago aceptados (FASE4-03).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code payment_method}:
 * 'efectivo', 'tarjeta', 'transferencia', 'otro'.
 */
public enum PaymentMethod {
  efectivo,
  tarjeta,
  transferencia,
  otro
}
