package com.julio.odentix.odentix_backend.crm.entity;

/**
 * Tipos de actividades o interacciones de contacto comercial con un lead (FASE5-01).
 *
 * <p>Los valores coinciden con la restricción check de base de datos en {@code lead_activities.activity_type}:
 * 'llamada', 'whatsapp', 'email', 'nota'.
 */
public enum LeadActivityType {
  llamada,
  whatsapp,
  email,
  nota
}
