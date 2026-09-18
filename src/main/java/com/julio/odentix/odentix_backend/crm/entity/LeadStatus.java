package com.julio.odentix.odentix_backend.crm.entity;

/**
 * Estados del pipeline comercial de un prospecto/lead en el CRM (FASE5-01).
 *
 * <p>Los valores coinciden exactamente con el tipo enumerado PostgreSQL {@code lead_status}:
 * 'nuevo', 'contactado', 'calificado', 'cita_propuesta', 'cita_agendada',
 * 'cita_asistida', 'tratamiento_propuesto', 'tratamiento_aceptado', 'perdido'.
 */
public enum LeadStatus {
  nuevo,
  contactado,
  calificado,
  cita_propuesta,
  cita_agendada,
  cita_asistida,
  tratamiento_propuesto,
  tratamiento_aceptado,
  perdido
}
