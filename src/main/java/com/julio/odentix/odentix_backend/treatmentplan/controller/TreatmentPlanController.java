package com.julio.odentix.odentix_backend.treatmentplan.controller;

import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.TreatmentPlanResponse;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanStatusRequest;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.service.TreatmentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Controlador REST para planes de tratamiento odontológico y su ciclo de vida (FASE4-02).
 */
@RestController
@RequestMapping("/api/v1/treatment-plans")
@Tag(name = "Planes de Tratamiento", description = "Gestión de propuestas clínicas, procedimientos, costos y máquina de estados.")
@SecurityRequirement(name = "bearerAuth")
public class TreatmentPlanController {

  private final TreatmentPlanService treatmentPlanService;

  public TreatmentPlanController(TreatmentPlanService treatmentPlanService) {
    this.treatmentPlanService = treatmentPlanService;
  }

  /**
   * Crea un nuevo plan de tratamiento con sus ítems iniciales.
   *
   * @param request datos del plan a registrar.
   * @return plan creado con estado HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO')")
  @Operation(
      summary = "Crear plan de tratamiento",
      description = "Crea un plan en estado 'borrador' con procedimientos y costos asociados. Reservado a facultativos."
  )
  public ResponseEntity<TreatmentPlanResponse> createTreatmentPlan(
      @Valid @RequestBody CreateTreatmentPlanRequest request) {
    TreatmentPlanResponse response = treatmentPlanService.createTreatmentPlan(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Consulta un plan de tratamiento por ID con sus ítems.
   *
   * @param id identificador único del plan.
   * @return detalle del plan e ítems.
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Consultar plan de tratamiento",
      description = "Devuelve los datos del plan y sus ítems dentro del tenant activo."
  )
  public ResponseEntity<TreatmentPlanResponse> getTreatmentPlan(@PathVariable UUID id) {
    return ResponseEntity.ok(treatmentPlanService.getTreatmentPlan(id));
  }

  /**
   * Lista los planes de tratamiento del tenant activo con filtros opcionales.
   *
   * @param patientId filtro opcional por paciente.
   * @param status filtro opcional por estado clínico.
   * @return lista de planes coincidentes.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Listar planes de tratamiento",
      description = "Consulta planes del tenant activo. Permite filtrar opcionalmente por paciente o estado del plan."
  )
  public ResponseEntity<List<TreatmentPlanResponse>> listTreatmentPlans(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) TreatmentPlanStatus status) {
    return ResponseEntity.ok(treatmentPlanService.listTreatmentPlans(patientId, status));
  }

  /**
   * Actualiza el diagnóstico, profesional o ítems de un plan en estado borrador.
   *
   * @param id identificador del plan.
   * @param request datos a modificar.
   * @return plan actualizado.
   */
  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO')")
  @Operation(
      summary = "Modificar plan de tratamiento",
      description = "Actualiza datos o procedimientos de un plan. La modificación de ítems solo está permitida en estado 'borrador'."
  )
  public ResponseEntity<TreatmentPlanResponse> updateTreatmentPlan(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTreatmentPlanRequest request) {
    return ResponseEntity.ok(treatmentPlanService.updateTreatmentPlan(id, request));
  }

  /**
   * Avanza el plan de tratamiento a través de su máquina de estados.
   *
   * @param id identificador del plan.
   * @param request nuevo estado destino.
   * @return plan con el nuevo estado y timestamps calculados.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'RECEPCION')")
  @Operation(
      summary = "Avanzar estado del plan",
      description = "Transiciona el plan según la máquina de estados. Las transiciones inválidas devuelven HTTP 400."
  )
  public ResponseEntity<TreatmentPlanResponse> updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateTreatmentPlanStatusRequest request) {
    return ResponseEntity.ok(treatmentPlanService.updateStatus(id, request));
  }
}
