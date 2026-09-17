package com.julio.odentix.odentix_backend.patient.entity;

/**
 * Tipo de entrada de odontograma (FASE2-07).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * `odontogram_entry_type` (docs/schema.sql §2 y migración V8): separan el
 * estado actual, el diagnóstico, el plan propuesto y el tratamiento
 * realizado para no mezclar información clínica de distintos momentos
 * (sección 8.4 del doc de arquitectura).
 */
public enum OdontogramEntryType {
  estado_actual,
  diagnostico,
  plan_propuesto,
  tratamiento_realizado
}
