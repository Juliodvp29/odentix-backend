package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.ConvertWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateWaitlistStatusRequest;
import com.julio.odentix.odentix_backend.appointment.dto.WaitlistEntryResponse;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.service.WaitlistService;
import com.julio.odentix.odentix_backend.shared.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

@RestController
@RequestMapping("/api/v1/waitlist")
@Tag(name = "Lista de espera", description = "Gestión del ciclo de vida de pacientes interesados en recuperar un horario.")
@SecurityRequirement(name = "bearerAuth")
public class WaitlistController {

  private final WaitlistService waitlistService;

  public WaitlistController(WaitlistService waitlistService) {
    this.waitlistService = waitlistService;
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Registrar interesado en lista de espera",
      description = "Crea una entrada en estado activa para un paciente del tenant activo."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Entrada creada",
          content = @Content(schema = @Schema(implementation = WaitlistEntryResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Datos inválidos",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "No autenticado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "Rol sin permiso",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Paciente no encontrado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<WaitlistEntryResponse> addEntry(
      @Valid @RequestBody CreateWaitlistEntryRequest request) {
    WaitlistEntryResponse response = waitlistService.addEntry(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Listar entradas de lista de espera",
      description = "Devuelve una página del tenant activo con filtros opcionales por estado y nombre o teléfono."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Página de entradas",
          content = @Content(schema = @Schema(implementation = Page.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Filtro o paginación inválidos",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "No autenticado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "Rol sin permiso",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<Page<WaitlistEntryResponse>> listEntries(
      @RequestParam(required = false) WaitlistStatus status,
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(waitlistService.listEntries(status, query, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Consultar una entrada de lista de espera",
      description = "Obtiene una entrada por ID dentro del tenant autenticado."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Entrada encontrada",
          content = @Content(schema = @Schema(implementation = WaitlistEntryResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "No autenticado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "Rol sin permiso",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Entrada no encontrada",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<WaitlistEntryResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(waitlistService.getById(id));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Cambiar estado de una entrada",
      description = "Permite activa a contactada o descartada, y contactada a descartada. La conversión requiere el endpoint de conversión."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Entrada actualizada o estado repetido",
          content = @Content(schema = @Schema(implementation = WaitlistEntryResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Solicitud inválida",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "No autenticado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "Rol sin permiso",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Entrada no encontrada",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Transición no permitida",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<WaitlistEntryResponse> updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWaitlistStatusRequest request) {
    return ResponseEntity.ok(waitlistService.updateStatus(id, request));
  }

  @PostMapping("/{id}/convert")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Convertir una entrada en una cita",
      description = "Crea una cita atómica para el paciente de la entrada a partir de una cita origen cancelada y compatible."
  )
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Cita creada",
          content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
      @ApiResponse(
          responseCode = "200",
          description = "La entrada ya estaba convertida; se devuelve la misma cita",
          content = @Content(schema = @Schema(implementation = AppointmentResponse.class))),
      @ApiResponse(
          responseCode = "400",
          description = "Cita origen o compatibilidad inválida",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "401",
          description = "No autenticado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "403",
          description = "Rol sin permiso",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "404",
          description = "Entrada o cita origen no encontrada",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
      @ApiResponse(
          responseCode = "409",
          description = "Estado incompatible o horario ocupado",
          content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  public ResponseEntity<AppointmentResponse> convert(
      @PathVariable UUID id,
      @Valid @RequestBody ConvertWaitlistEntryRequest request) {
    WaitlistService.ConversionResult result = waitlistService.convert(id, request);
    if (result.created()) {
      return ResponseEntity.status(201).body(result.appointment());
    }
    return ResponseEntity.ok(result.appointment());
  }
}
