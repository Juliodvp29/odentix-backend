package com.julio.odentix.odentix_backend.task.dto;

import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud para actualizar una tarea (FASE8-01).
 *
 * <p>Todos los campos son opcionales (PATCH): solo los no nulos se aplican.
 * El estado no se edita por aquí — solo vía el endpoint de completar.
 * Sin Lombok.
 */
public class UpdateTaskRequest {

  private String title;

  private String description;

  private UUID assignedTo;

  private Instant dueAt;

  private TaskPriority priority;

  public UpdateTaskRequest() {
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public UUID getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(UUID assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public void setDueAt(Instant dueAt) {
    this.dueAt = dueAt;
  }

  public TaskPriority getPriority() {
    return priority;
  }

  public void setPriority(TaskPriority priority) {
    this.priority = priority;
  }
}

