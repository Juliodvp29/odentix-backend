package com.julio.odentix.odentix_backend.opportunity.controller;

import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityResponse;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.service.OpportunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para oportunidades de negocio (FASE9-01).
 *
 * <p>Expone la bandeja de oportunidades detectadas por el motor de reglas.
 * El acceso es amplio: propietarios, odontólogos y recepción necesitan ver
 * las oportunidades para actuar sobre ellas.
 */
@RestController
@RequestMapping("/api/v1/opportunities")
@Tag(name = "Oportunidades",
    description = "Bandeja de oportunidades de negocio detectadas automáticamente.")
@SecurityRequirement(name = "bearerAuth")
public class OpportunityController {

  private static final String ROLES_OPORTUNIDADES =
      "hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')";

  private final OpportunityService opportunityService;

  public OpportunityController(OpportunityService opportunityService) {
    this.opportunityService = opportunityService;
  }

  /**
   * Lista las oportunidades del tenant activo.
   *
   * <p>Si se pasa {@code status}, filtra por ese estado; si no, devuelve todas.
   * Resultado ordenado por prioridad descendente y fecha de detección descendente.
   */
  @GetMapping
  @PreAuthorize(ROLES_OPORTUNIDADES)
  @Operation(
      summary = "Listar oportunidades",
      description = "Lista las oportunidades detectadas para el tenant activo, "
          + "ordenadas por prioridad. Opcionalmente filtra por estado."
  )
  public ResponseEntity<List<OpportunityResponse>> listarOportunidades(
      @RequestParam(required = false) OpportunityStatus status) {
    return ResponseEntity.ok(opportunityService.listarOportunidades(status));
  }
}
