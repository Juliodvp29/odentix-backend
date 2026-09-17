package com.julio.odentix.odentix_backend.appointment.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Agregado del valor estimado de la agenda en un rango (FASE3-05).
 *
 * <p>Insumo para reportes y para el motor de oportunidades (Fase 9): solo
 * el dato, sin lógica de riesgo ni priorización.
 */
public class AppointmentValueSummary {

  private final Instant from;
  private final Instant to;
  private final BigDecimal totalCop;
  private final long appointmentCount;

  public AppointmentValueSummary(Instant from, Instant to, BigDecimal totalCop, long appointmentCount) {
    this.from = from;
    this.to = to;
    this.totalCop = totalCop;
    this.appointmentCount = appointmentCount;
  }

  public Instant getFrom() {
    return from;
  }

  public Instant getTo() {
    return to;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }

  public long getAppointmentCount() {
    return appointmentCount;
  }
}
