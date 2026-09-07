package com.julio.odentix.odentix_backend.patient.controller;

import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.dto.UpdatePatientRequest;
import com.julio.odentix.odentix_backend.patient.service.PatientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.http.HttpStatus;

/**
 * CRUD de pacientes (FASE2-02). Sin paginación ni búsqueda todavía
 * (FASE2-03) y sin restricción por rol: cualquier usuario autenticado
 * opera sobre los pacientes de su propio tenant.
 *
 * <p>El manejo de errores es local a este controlador; en FASE2-03 migra
 * al {@code @ControllerAdvice} global con el formato estándar que
 * reutilizarán todos los módulos.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

  private final PatientService patientService;

  public PatientController(PatientService patientService) {
    this.patientService = patientService;
  }

  @PostMapping
  public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
    PatientResponse created = patientService.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}")
  public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(patientService.getById(id));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<PatientResponse> patch(
      @PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
    return ResponseEntity.ok(patientService.patch(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    // Baja lógica: marca is_active = false, no borra la fila (FASE2-02).
    patientService.deactivate(id);
    return ResponseEntity.noContent().build();
  }

  // POST/PATCH con datos inválidos → 400 con el campo que falló (DoD FASE2-02).
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Inválido",
            (first, second) -> first));
    return Map.of("errors", fieldErrors);
  }

  // Documento duplicado dentro del tenant → 409, no un 500 genérico
  // (el unique uq_patients_tenant_document se creó en FASE2-01).
  @ExceptionHandler(DataIntegrityViolationException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, String> handleDuplicateDocument(DataIntegrityViolationException ex) {
    return Map.of("error", "Ya existe un paciente con ese número de documento");
  }
}
