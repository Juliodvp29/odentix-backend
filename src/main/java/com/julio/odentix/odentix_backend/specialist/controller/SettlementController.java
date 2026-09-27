package com.julio.odentix.odentix_backend.specialist.controller;

import com.julio.odentix.odentix_backend.specialist.dto.CreateSettlementRequest;
import com.julio.odentix.odentix_backend.specialist.dto.SettlementBreakdownResponse;
import com.julio.odentix.odentix_backend.specialist.dto.SettlementResponse;
import com.julio.odentix.odentix_backend.specialist.dto.SpecialistResponse;
import com.julio.odentix.odentix_backend.specialist.service.SettlementService;
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
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
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

  /**
   * Lista los especialistas externos del tenant activo con sus condiciones.
   *
   * @return especialistas ordenados por nombre.
   */
  @GetMapping
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
  @Operation(
      summary = "Listar especialistas",
      description = "Obtiene los especialistas externos del tenant con su porcentaje de honorarios."
  )
  public ResponseEntity<List<SpecialistResponse>> listarEspecialistas() {
    return ResponseEntity.ok(settlementService.listarEspecialistas());
  }

  /**
   * Lista las liquidaciones de un especialista ordenadas por periodo.
   *
   * @param id identificador del especialista dentro del tenant activo.
   * @return liquidaciones del especialista (404 si es de otro tenant).
   */
  @GetMapping("/{id}/settlements")
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
  @Operation(
      summary = "Listar liquidaciones",
      description = "Obtiene las liquidaciones de un especialista ordenadas por inicio de periodo. "
          + "Devuelve 404 si el especialista pertenece a otro tenant."
  )
  public ResponseEntity<List<SettlementResponse>> listarLiquidaciones(
      @PathVariable UUID id) {
    return ResponseEntity.ok(settlementService.listarLiquidaciones(id));
  }

  /**
   * Consulta una liquidación por ID dentro de un especialista.
   *
   * @param specialistId identificador del especialista dentro del tenant activo.
   * @param settlementId identificador de la liquidación.
   * @return liquidación encontrada (404 si es de otro tenant o especialista).
   */
  @GetMapping("/{specialistId}/settlements/{settlementId}")
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
  @Operation(
      summary = "Consultar liquidación",
      description = "Obtiene el detalle de una liquidación. "
          + "Devuelve 404 si pertenece a otro tenant o a otro especialista."
  )
  public ResponseEntity<SettlementResponse> obtenerLiquidacion(
      @PathVariable UUID specialistId,
      @PathVariable UUID settlementId) {
    return ResponseEntity.ok(settlementService.obtenerLiquidacion(specialistId, settlementId));
  }

  /**
   * Desglosa una liquidación en las facturas que componen su producción bruta.
   *
   * @param specialistId identificador del especialista dentro del tenant activo.
   * @param settlementId identificador de la liquidación.
   * @return facturas del periodo ordenadas por emisión con los totales (404
   *     si la liquidación es de otro tenant o especialista).
   */
  @GetMapping("/{specialistId}/settlements/{settlementId}/breakdown")
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
  @Operation(
      summary = "Desglosar liquidación",
      description = "Obtiene las facturas que componen la producción bruta de la liquidación. "
          + "Devuelve 404 si pertenece a otro tenant o a otro especialista."
  )
  public ResponseEntity<SettlementBreakdownResponse> obtenerDesglose(
      @PathVariable UUID specialistId,
      @PathVariable UUID settlementId) {
    return ResponseEntity.ok(settlementService.obtenerDesglose(specialistId, settlementId));
  }
}
