package com.julio.odentix.odentix_backend.billing.entity;

/**
 * Estados de una factura (FASE4-03).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code invoice_status}:
 * 'pendiente', 'parcial', 'pagada', 'anulada'. La transición entre estados
 * según pagos acumulados la implementa el servicio en FASE4-04.
 */
public enum InvoiceStatus {
  pendiente,
  parcial,
  pagada,
  anulada
}
