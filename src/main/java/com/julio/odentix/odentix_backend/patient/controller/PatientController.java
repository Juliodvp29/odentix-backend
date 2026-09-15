package com.julio.odentix.odentix_backend.patient.controller;

import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.dto.UpdatePatientRequest;
import com.julio.odentix.odentix_backend.patient.service.PatientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CRUD de pacientes con paginación, búsqueda y manejo centralizado de errores (FASE2-02 / FASE2-03).
 *
 * <p>Sin restricción por rol: cualquier usuario autenticado opera sobre los pacientes de su propio tenant.
 * Los errores son gestionados de forma transversal por {@link com.julio.odentix.odentix_backend.shared.exception.GlobalExceptionHandler}.
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

  @GetMapping
  public ResponseEntity<Page<PatientResponse>> list(
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(patientService.search(query, pageable));
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
}
