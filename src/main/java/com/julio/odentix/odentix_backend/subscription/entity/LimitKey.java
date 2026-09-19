package com.julio.odentix.odentix_backend.subscription.entity;

/**
 * Claves de límites numéricos por plan (FASE11-01).
 *
 * <p>Los valores coinciden con la columna {@code plan_limits.limit_key}
 * sembrada en V25 (docs/schema.sql §17). {@code NULL} en {@code max_value}
 * significa "ilimitado". Igual que {@link FeatureKey}, son constantes String
 * porque la columna es TEXT libre. No renombrar sin señalarlo (regla §10).
 */
public final class LimitKey {

  public static final String MAX_SEDES = "max_sedes";
  public static final String MAX_USERS = "max_users";
  public static final String MAX_PATIENTS = "max_patients";
  public static final String WHATSAPP_CONVERSATIONS_MONTH = "whatsapp_conversations_month";
  public static final String MAX_SPECIALISTS = "max_specialists";
  public static final String OPPORTUNITIES_MAX_RULES = "opportunities_max_rules";

  private LimitKey() {
  }
}
