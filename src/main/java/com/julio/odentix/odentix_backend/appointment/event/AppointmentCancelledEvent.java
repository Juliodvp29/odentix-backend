package com.julio.odentix.odentix_backend.appointment.event;

import java.util.UUID;

/**
 * Se publica cuando una cita transiciona realmente a {@code cancelada} (FASE8-06).
 *
 * <p>Evento de dominio para desacoplar la automatización del servicio de citas:
 * el módulo `task` reacciona sin que `appointment` dependa de él (evita un
 * ciclo de dependencias, ya que el job de FASE8-02 va en la dirección
 * contraria `task` → `appointment`).
 *
 * <p>Lleva el tenant explícito porque los listeners pueden correr fuera del
 * contexto de la request original.
 */
public record AppointmentCancelledEvent(UUID appointmentId, UUID tenantId) {
}
