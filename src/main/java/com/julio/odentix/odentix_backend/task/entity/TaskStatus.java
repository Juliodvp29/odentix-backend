package com.julio.odentix.odentix_backend.task.entity;

/**
 * Estados de una tarea (FASE8-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code task_status}:
 * 'pendiente', 'en_progreso', 'completada', 'cancelada'. Las reglas automáticas
 * de FASE8-02 crean tareas en {@code pendiente}; el endpoint de FASE8-01 solo
 * transiciona a {@code completada}.
 */
public enum TaskStatus {
  pendiente,
  en_progreso,
  completada,
  cancelada
}
