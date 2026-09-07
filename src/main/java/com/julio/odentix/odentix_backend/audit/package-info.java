/**
 * Sistema de auditoría transversal (FASE1-13): quién hizo qué, cuándo y en
 * qué tenant. Tabla append-only: se escribe vía {@code AuditService} y se
 * consulta filtrada por tenant; nunca se actualiza ni se borra.
 */
package com.julio.odentix.odentix_backend.audit;
