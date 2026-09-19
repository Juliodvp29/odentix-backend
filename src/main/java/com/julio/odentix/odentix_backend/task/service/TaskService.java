package com.julio.odentix.odentix_backend.task.service;

import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.task.dto.CreateTaskRequest;
import com.julio.odentix.odentix_backend.task.dto.TaskResponse;
import com.julio.odentix.odentix_backend.task.dto.UpdateTaskRequest;
import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para tareas (FASE8-01).
 *
 * <p>CRUD manual: crear, listar, ver, actualizar y completar. Las reglas
 * automáticas de creación (FASE8-02) reutilizarán este servicio para no
 * duplicar la validación del responsable.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: una tarea de otro
 * tenant resulta invisible (404), conforme a la regla §5 de AGENTS.md.
 */
@Service
public class TaskService {

  private final TaskRepository taskRepository;
  private final UserRepository userRepository;

  public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
    this.taskRepository = taskRepository;
    this.userRepository = userRepository;
  }

  /**
   * Crea una tarea en estado {@code pendiente}.
   *
   * <p>Si trae responsable, se verifica que el usuario exista en el tenant
   * activo (404 si no): evita tareas asignadas a usuarios fantasma o de otra
   * clínica.
   */
  @Transactional
  public TaskResponse crearTarea(CreateTaskRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    if (request.getAssignedTo() != null) {
      validarResponsable(tenantId, request.getAssignedTo());
    }

    Task tarea = new Task(tenantId, request.getTitle().strip());
    tarea.setDescription(request.getDescription());
    tarea.setRelatedEntityType(request.getRelatedEntityType());
    tarea.setRelatedEntityId(request.getRelatedEntityId());
    tarea.setAssignedTo(request.getAssignedTo());
    tarea.setDueAt(request.getDueAt());
    if (request.getPriority() != null) {
      tarea.setPriority(request.getPriority());
    }
    return aResponse(taskRepository.save(tarea));
  }

  /**
   * Lista todas las tareas del tenant activo.
   */
  @Transactional(readOnly = true)
  public List<TaskResponse> listarTareas() {
    TenantContext.getRequiredTenantId();
    return taskRepository.findAll().stream()
        .map(this::aResponse)
        .toList();
  }

  /**
   * Tareas asignadas al usuario autenticado ("mis tareas").
   */
  @Transactional(readOnly = true)
  public List<TaskResponse> misTareas(UUID currentUserId) {
    TenantContext.getRequiredTenantId();
    return taskRepository.findByAssignedTo(currentUserId).stream()
        .map(this::aResponse)
        .toList();
  }

  /**
   * Obtiene una tarea por ID dentro del tenant activo.
   */
  @Transactional(readOnly = true)
  public TaskResponse obtenerTarea(UUID id) {
    return aResponse(buscarTarea(id));
  }

  /**
   * Actualiza título, descripción, responsable, vencimiento y/o prioridad.
   * El estado no se edita por aquí — solo vía completar.
   */
  @Transactional
  public TaskResponse actualizarTarea(UUID id, UpdateTaskRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Task tarea = buscarTarea(id);

    if (request.getTitle() != null && !request.getTitle().isBlank()) {
      tarea.setTitle(request.getTitle().strip());
    }
    if (request.getDescription() != null) {
      tarea.setDescription(request.getDescription());
    }
    if (request.getAssignedTo() != null) {
      validarResponsable(tenantId, request.getAssignedTo());
      tarea.setAssignedTo(request.getAssignedTo());
    }
    if (request.getDueAt() != null) {
      tarea.setDueAt(request.getDueAt());
    }
    if (request.getPriority() != null) {
      tarea.setPriority(request.getPriority());
    }
    return aResponse(taskRepository.save(tarea));
  }

  /**
   * Marca una tarea como {@code completada} (idempotente: repetirlo devuelve
   * la misma tarea). Una tarea {@code cancelada} no se puede completar.
   */
  @Transactional
  public TaskResponse completarTarea(UUID id) {
    Task tarea = buscarTarea(id);

    if (tarea.getStatus() == TaskStatus.cancelada) {
      throw new ConflictException("La tarea está cancelada y no se puede completar.");
    }
    tarea.setStatus(TaskStatus.completada);
    return aResponse(taskRepository.save(tarea));
  }

  /**
   * Elimina una tarea (las tareas son operativas, sin historial que proteger).
   */
  @Transactional
  public void eliminarTarea(UUID id) {
    taskRepository.delete(buscarTarea(id));
  }

  private Task buscarTarea(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    return taskRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Tarea no encontrada: " + id));
  }

  private void validarResponsable(UUID tenantId, UUID assignedTo) {
    userRepository.findByIdAndTenantId(assignedTo, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Usuario responsable no encontrado: " + assignedTo));
  }

  private TaskResponse aResponse(Task tarea) {
    TaskResponse response = new TaskResponse();
    response.setId(tarea.getId());
    response.setTitle(tarea.getTitle());
    response.setDescription(tarea.getDescription());
    response.setRelatedEntityType(tarea.getRelatedEntityType());
    response.setRelatedEntityId(tarea.getRelatedEntityId());
    response.setAssignedTo(tarea.getAssignedTo());
    response.setDueAt(tarea.getDueAt());
    response.setPriority(tarea.getPriority());
    response.setStatus(tarea.getStatus());
    return response;
  }
}
