package com.julio.odentix.odentix_backend.tenant.controller;

import com.julio.odentix.odentix_backend.tenant.dto.TenantSettingsResponse;
import com.julio.odentix.odentix_backend.tenant.dto.UpdateTenantSettingsRequest;
import com.julio.odentix.odentix_backend.tenant.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de ajustes de la propia clínica (pre-Fase 12).
 *
 * <p>Solo el propietario edita, y siempre su propia clínica (del contexto).
 */
@RestController
@RequestMapping("/api/v1/tenant")
@Tag(name = "Clínica", description = "Ajustes de la propia clínica.")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

  private final TenantService tenantService;

  public TenantController(TenantService tenantService) {
    this.tenantService = tenantService;
  }

  /**
   * Ajustes actuales (remitente de notificaciones, etc.).
   */
  @GetMapping("/settings")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(summary = "Ver ajustes", description = "Ajustes de la clínica autenticada.")
  public ResponseEntity<TenantSettingsResponse> obtenerAjustes() {
    return ResponseEntity.ok(tenantService.obtenerAjustes());
  }

  /**
   * Actualiza el remitente de notificaciones de la clínica.
   */
  @PatchMapping("/settings")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(
      summary = "Actualizar ajustes",
      description = "Cambia el correo/nombre remitente de la clínica. "
          + "Vacío vuelve al remitente global."
  )
  public ResponseEntity<TenantSettingsResponse> actualizarAjustes(
      @Valid @RequestBody UpdateTenantSettingsRequest request) {
    return ResponseEntity.ok(tenantService.actualizarAjustes(request));
  }
}
