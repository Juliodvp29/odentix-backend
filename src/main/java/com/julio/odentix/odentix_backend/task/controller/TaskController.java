package com.julio.odentix.odentix_backend.task.controller;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.task.dto.CreateTaskRequest;
import com.julio.odentix.odentix_backend.task.dto.TaskResponse;
import com.julio.odentix.odentix_backend.task.dto.UpdateTaskRequest;
import com.julio.odentix.odentix_backend.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para tareas (FASE8-01).
 *
 * <p>Roles operativos amplios: las tareas las crean y completan todos los
 * roles internos en la práctica diaria.
 */
@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "Tareas", description = "Tareas operativas: creación manual, asignación y completado.")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

  private static final String ROLES_OPERATIVOS =
      "hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')";

  private final TaskService taskService;

  public TaskController(TaskService taskService) {
    this.taskService = taskService;
  }

  /**
   * Crea una tarea en estado pendiente, opcionalmente asignada.
   */
  @PostMapping
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Crear tarea",
      description = "Crea una tarea manual en estado pendiente. "
          + "Si trae responsable, debe ser un usuario del tenant (404 si no)."
  )
  public ResponseEntity<TaskResponse> crearTarea(
      @Valid @RequestBody CreateTaskRequest request) {
    TaskResponse response = taskService.crearTarea(request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/tasks/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Lista todas las tareas del tenant activo.
   */
  @GetMapping
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Listar tareas", description = "Lista todas las tareas del tenant activo.")
  public ResponseEntity<List<TaskResponse>> listarTareas() {
    return ResponseEntity.ok(taskService.listarTareas());
  }

  /**
   * Tareas asignadas al usuario autenticado.
   */
  @GetMapping("/mine")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Mis tareas", description = "Tareas asignadas al usuario autenticado.")
  public ResponseEntity<List<TaskResponse>> misTareas(
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    UUID currentUserId = (authUser != null) ? authUser.getUserId() : null;
    return ResponseEntity.ok(taskService.misTareas(currentUserId));
  }

  /**
   * Obtiene una tarea por ID.
   */
  @GetMapping("/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Ver tarea", description = "Obtiene una tarea por ID.")
  public ResponseEntity<TaskResponse> obtenerTarea(@PathVariable UUID id) {
    return ResponseEntity.ok(taskService.obtenerTarea(id));
  }

  /**
   * Actualiza título, descripción, responsable, vencimiento y/o prioridad.
   */
  @PatchMapping("/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Actualizar tarea",
      description = "Actualiza los datos de una tarea. El estado no se edita por aquí."
  )
  public ResponseEntity<TaskResponse> actualizarTarea(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTaskRequest request) {
    return ResponseEntity.ok(taskService.actualizarTarea(id, request));
  }

  /**
   * Marca una tarea como completada.
   */
  @PostMapping("/{id}/complete")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Completar tarea",
      description = "Marca una tarea como completada. Idempotente; "
          + "una tarea cancelada devuelve 409."
  )
  public ResponseEntity<TaskResponse> completarTarea(@PathVariable UUID id) {
    return ResponseEntity.ok(taskService.completarTarea(id));
  }

  /**
   * Elimina una tarea.
   */
  @DeleteMapping("/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Eliminar tarea", description = "Elimina una tarea.")
  public ResponseEntity<Void> eliminarTarea(@PathVariable UUID id) {
    taskService.eliminarTarea(id);
    return ResponseEntity.noContent().build();
  }
}
