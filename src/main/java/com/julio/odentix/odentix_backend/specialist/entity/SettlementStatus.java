package com.julio.odentix.odentix_backend.specialist.entity;

/**
 * Estados de una liquidación de especialista (FASE7-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code settlement_status}:
 * 'pendiente', 'pagada'. La transición a {@code pagada} la implementa el endpoint
 * de FASE7-02.
 */
public enum SettlementStatus {
  pendiente,
  pagada
}
