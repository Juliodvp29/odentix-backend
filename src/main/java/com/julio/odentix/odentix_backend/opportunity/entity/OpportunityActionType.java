package com.julio.odentix.odentix_backend.opportunity.entity;

/**
 * Tipos de acción sugerida por oportunidad (FASE9-03).
 *
 * <p>Se guardan como TEXT en `opportunity_actions.action_type` (fiel a
 * `schema.sql`): 'crear_tarea' crea una `Task`, 'enviar_mensaje' envía por el
 * `channel` indicado vía `NotificationService`.
 */
public enum OpportunityActionType {
  crear_tarea,
  enviar_mensaje
}
