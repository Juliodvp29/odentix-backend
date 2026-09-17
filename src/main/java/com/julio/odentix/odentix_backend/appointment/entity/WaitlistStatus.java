package com.julio.odentix.odentix_backend.appointment.entity;

/**
 * Estados de un interesado en lista de espera (FASE3-06).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code waitlist_status}:
 * 'activa', 'contactado', 'convertida', 'descartada'. Toda entrada nace
 * {@code activa}; los demás estados los mueve FASE3-07.
 */
public enum WaitlistStatus {
  activa,
  contactado,
  convertida,
  descartada
}
