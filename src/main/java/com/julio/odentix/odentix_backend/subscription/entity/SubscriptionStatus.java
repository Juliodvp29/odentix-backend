package com.julio.odentix.odentix_backend.subscription.entity;

/**
 * Estado de la suscripción de un tenant (FASE11-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * {@code subscription_status} (docs/schema.sql §2):
 * 'trialing', 'active', 'past_due', 'cancelled'.
 */
public enum SubscriptionStatus {
  trialing,
  active,
  past_due,
  cancelled
}
