package com.julio.odentix.odentix_backend.treatmentplan.entity;

/**
 * Estados del ciclo de vida de un plan de tratamiento odontológico (FASE4-01 y FASE4-02).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code treatment_plan_status}:
 * 'borrador', 'presentado', 'en_decision', 'aceptado', 'en_ejecucion', 'completado',
 * 'rechazado', 'pospuesto', 'abandonado'.
 */
public enum TreatmentPlanStatus {
  borrador,
  presentado,
  en_decision,
  aceptado,
  en_ejecucion,
  completado,
  rechazado,
  pospuesto,
  abandonado
}
