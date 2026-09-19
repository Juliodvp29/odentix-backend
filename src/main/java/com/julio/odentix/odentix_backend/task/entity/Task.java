package com.julio.odentix.odentix_backend.task.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Tarea operativa de la clínica (FASE8-01).
 *
 * <p>Puede crearse manualmente (este ticket) o automáticamente por reglas
 * (FASE8-02). La referencia polimórfica {@code relatedEntityType} /
 * {@code relatedEntityId} apunta a cualquier entidad de negocio (lead, cita,
 * tratamiento…) y por eso no lleva FK real — ver nota en {@code schema.sql}
 * §14. El responsable ({@code assignedTo}) se modela como UUID simple
 * (precedente {@code ClinicalRecord.professionalId}).
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "tasks")
public class Task extends TenantAwareEntity {

  @Column(name = "title", nullable = false, columnDefinition = "TEXT")
  private String title;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "related_entity_type")
  private String relatedEntityType;

  @Column(name = "related_entity_id")
  private UUID relatedEntityId;

  @Column(name = "assigned_to")
  private UUID assignedTo;

  @Column(name = "due_at")
  private Instant dueAt;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "priority", nullable = false)
  private TaskPriority priority = TaskPriority.media;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private TaskStatus status = TaskStatus.pendiente;

  public Task() {
    super();
  }

  public Task(UUID tenantId, String title) {
    super(tenantId);
    this.title = title;
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
    this.priority = priority != null ? priority : TaskPriority.media;
  }

  public TaskStatus getStatus() {
    return status;
  }

  public void setStatus(TaskStatus status) {
    this.status = status != null ? status : TaskStatus.pendiente;
  }

  @Override
  public String toString() {
    return "Task{"
        + "id=" + getId()
        + ", title='" + title + '\''
        + ", status=" + status
        + '}';
  }
}
