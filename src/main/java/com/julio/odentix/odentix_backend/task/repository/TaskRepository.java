package com.julio.odentix.odentix_backend.task.repository;

import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Task} (FASE8-01).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

  /**
   * Busca una tarea por su ID y tenant (defensa en profundidad por tenant).
   */
  Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Tareas asignadas a un usuario (base del endpoint "mis tareas").
   */
  List<Task> findByAssignedTo(UUID assignedTo);

  /**
   * Tareas por estado (insumo de las reglas automáticas de FASE8-02, que no
   * deben duplicar tareas ya abiertas para el mismo evento).
   */
  List<Task> findByStatus(TaskStatus status);

  /**
   * Indica si ya existe una tarea abierta (no completada ni cancelada) para
   * una entidad de negocio — evita que las reglas automáticas dupliquen
   * tareas en cada corrida (idempotencia de FASE8-02 y FASE9-01).
   */
  boolean existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
      String relatedEntityType, UUID relatedEntityId, List<TaskStatus> statuses);
}

