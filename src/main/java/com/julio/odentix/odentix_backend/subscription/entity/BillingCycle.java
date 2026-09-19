package com.julio.odentix.odentix_backend.subscription.entity;

/**
 * Ciclo de facturación de la suscripción (FASE11-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * {@code billing_cycle} (docs/schema.sql §2): 'monthly', 'annual'.
 * El precio anual equivale a ~10 meses (descuento de ~2 meses gratis).
 */
public enum BillingCycle {
  monthly,
  annual
}
