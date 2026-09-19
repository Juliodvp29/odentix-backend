package com.julio.odentix.odentix_backend.subscription.entity;

/**
 * Claves de feature flags por plan (FASE11-01).
 *
 * <p>Los valores coinciden con la columna {@code plan_features.feature_key}
 * sembrada en V25 (docs/schema.sql §17). Se modelan como constantes String
 * —no como enum— porque la columna es TEXT libre: una feature futura solo
 * exige una fila semilla nueva, sin cambio de código. Las claves son parte
 * de una decisión ya tomada (regla §10 de AGENTS.md): no renombrarlas sin
 * señalarlo explícitamente.
 */
public final class FeatureKey {

  public static final String CRM_LEADS = "crm_leads";
  public static final String CARTERA = "cartera";
  public static final String AUTOMATIONS_FULL = "automations_full";
  public static final String SPECIALISTS = "specialists";
  public static final String INVENTORY = "inventory";
  public static final String INVENTORY_ALERTS = "inventory_alerts";
  public static final String OPPORTUNITIES_ENGINE = "opportunities_engine";
  public static final String AI_ASSISTANT = "ai_assistant";

  private FeatureKey() {
  }
}
