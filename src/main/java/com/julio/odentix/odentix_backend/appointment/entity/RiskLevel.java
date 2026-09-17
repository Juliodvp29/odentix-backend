package com.julio.odentix.odentix_backend.appointment.entity;

/**
 * Nivel de riesgo estimado de inasistencia / pérdida de una cita (FASE3-02 y FASE3-05).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code risk_level}:
 * 'bajo', 'medio', 'alto'.
 */
public enum RiskLevel {
  bajo,
  medio,
  alto
}
