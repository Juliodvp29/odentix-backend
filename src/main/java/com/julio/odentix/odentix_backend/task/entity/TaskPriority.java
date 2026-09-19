package com.julio.odentix.odentix_backend.task.entity;

/**
 * Prioridades de una tarea (FASE8-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code task_priority}:
 * 'baja', 'media', 'alta'.
 */
public enum TaskPriority {
  baja,
  media,
  alta
}
