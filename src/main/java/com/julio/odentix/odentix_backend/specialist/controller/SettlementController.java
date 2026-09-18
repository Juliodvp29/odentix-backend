package com.julio.odentix.odentix_backend.specialist.controller;

import com.julio.odentix.odentix_backend.specialist.dto.CreateSettlementRequest;
import com.julio.odentix.odentix_backend.specialist.dto.SettlementResponse;
import com.julio.odentix.odentix_backend.specialist.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para especialistas externos y liquidaciones (FASE7-02).
 */
@RestController
@RequestMapping("/api/v1/specialists")
@Tag(name = "Especialistas", description = "Especialistas externos y liquidación de honorarios.")
@SecurityRequirement(name = "bearerAuth")
public class SettlementController {

  private final SettlementService settlementService;

  public SettlementController(SettlementService settlementService) {
    this.settlementService = settlementService;
  }

  /**
   * Genera la liquidación de honorarios de un especialista para un periodo.
   *
   * <p>La producción bruta se calcula de las facturas emitidas en el periodo
   * vinculadas a tratamientos del profesional; los honorarios aplican el
   * porcentaje pactado. Un mismo periodo solo se liquida una vez (409).
   * Operación financiera sensible: solo el propietario puede ejecutarla.
   *
   * @param id identificador del especialista.
   * @param request inicio y fin del periodo (fin inclusivo).
   * @return liquidación creada en estado {@code pendiente}.
   */
  @PostMapping("/{id}/settlements")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(
      summary = "Generar liquidación",
      description = "Calcula la producción bruta facturada del especialista en el periodo "
          + "y genera su liquidación de honorarios. Devuelve 409 si el periodo ya fue liquidado."
  )
  public ResponseEntity<SettlementResponse> generarLiquidacion(
      @PathVariable UUID id,
      @Valid @RequestBody CreateSettlementRequest request) {
    SettlementResponse response = settlementService.generarLiquidacion(id, request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/specialists/{specialistId}/settlements/{settlementId}")
        .buildAndExpand(id, response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
