package com.julio.odentix.odentix_backend.appointment.entity;

/**
 * Estados del ciclo de vida de una cita odontológica (FASE3-02 y FASE3-04).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code appointment_status}:
 * 'programada', 'confirmada', 'atendida', 'no_show', 'cancelada'.
 */
public enum AppointmentStatus {
  programada,
  confirmada,
  atendida,
  no_show,
  cancelada
}
