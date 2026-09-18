package com.julio.odentix.odentix_backend.billing.entity;

/**
 * Estados de una cuota de pago (FASE6-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code installment_status}:
 * 'pendiente', 'pagada', 'vencida'. El job diario de FASE6-03 transiciona
 * automáticamente de {@code pendiente} a {@code vencida} cuando {@code due_date}
 * queda en el pasado. La transición a {@code pagada} la ejecuta el endpoint
 * de FASE6-02.
 */
public enum InstallmentStatus {
  pendiente,
  pagada,
  vencida
}
