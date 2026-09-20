package com.julio.odentix.odentix_backend.task.dto;

import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Tarea con su estado actual (FASE8-01). Sin Lombok.
 */
public class TaskResponse {

  private UUID id;
  private String title;
  private String description;
  private String relatedEntityType;
  private UUID relatedEntityId;
  private UUID assignedTo;
  private Instant dueAt;
  private TaskPriority priority;
  private TaskStatus status;

  public TaskResponse() {
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  public String getRelatedEntityType() {
    return relatedEntityType;
  }

  public void setRelatedEntityType(String relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
  }

  public UUID getRelatedEntityId() {
    return relatedEntityId;
  }

  public void setRelatedEntityId(UUID relatedEntityId) {
    this.relatedEntityId = relatedEntityId;
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

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status;
  }
}

