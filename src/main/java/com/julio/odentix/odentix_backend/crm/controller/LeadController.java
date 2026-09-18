package com.julio.odentix.odentix_backend.crm.controller;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadActivityRequest;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.LeadActivityResponse;
import com.julio.odentix.odentix_backend.crm.dto.LeadResponse;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadStatusRequest;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.service.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para la gestión de prospectos comerciales y embudo CRM (FASE5-02).
 */
@RestController
@RequestMapping("/api/v1/leads")
@Tag(name = "CRM Leads", description = "Gestión de prospectos comerciales, embudo de ventas e historial de interacciones.")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('PROPIETARIO', 'RECEPCION', 'ODONTOLOGO', 'AUXILIAR')")
public class LeadController {

  private final LeadService leadService;

  public LeadController(LeadService leadService) {
    this.leadService = leadService;
  }

  @PostMapping
  @Operation(
      summary = "Crear prospecto comercial",
      description = "Registra un nuevo lead en el embudo comercial en estado 'nuevo'."
  )
  public ResponseEntity<LeadResponse> createLead(@Valid @RequestBody CreateLeadRequest request) {
    LeadResponse response = leadService.createLead(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Consultar prospecto por ID",
      description = "Obtiene los detalles comerciales, datos de contacto y estado de un lead."
  )
  public ResponseEntity<LeadResponse> getLeadById(@PathVariable UUID id) {
    return ResponseEntity.ok(leadService.getLeadById(id));
  }

  @GetMapping
  @Operation(
      summary = "Listar prospectos comerciales",
      description = "Consulta paginada de leads con filtros opcionales por estado, responsable asignado y canal de origen."
  )
  public ResponseEntity<Page<LeadResponse>> listLeads(
      @RequestParam(required = false) LeadStatus status,
      @RequestParam(required = false) UUID assignedToId,
      @RequestParam(required = false) String source,
      @PageableDefault(size = 20) Pageable pageable) {
    return ResponseEntity.ok(leadService.listLeads(status, assignedToId, source, pageable));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Actualizar datos de prospecto",
      description = "Modifica información comercial, datos de contacto o asignación de un lead."
  )
  public ResponseEntity<LeadResponse> updateLead(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateLeadRequest request) {
    return ResponseEntity.ok(leadService.updateLead(id, request));
  }

  @PatchMapping("/{id}/status")
  @Operation(
      summary = "Actualizar estado en el pipeline",
      description = "Permite mover el prospecto entre las 9 etapas del embudo comercial, avanzando o retrocediendo según la interacción."
  )
  public ResponseEntity<LeadResponse> updateLeadStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateLeadStatusRequest request,
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    UUID currentUserId = (authUser != null) ? authUser.getUserId() : null;
    return ResponseEntity.ok(leadService.updateLeadStatus(id, request, currentUserId));
  }

  @PostMapping("/{id}/activities")
  @Operation(
      summary = "Registrar actividad de contacto",
      description = "Registra una interacción (llamada, WhatsApp, correo, nota) con el prospecto y actualiza la fecha de último contacto."
  )
  public ResponseEntity<LeadActivityResponse> addActivity(
      @PathVariable UUID id,
      @Valid @RequestBody CreateLeadActivityRequest request,
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    UUID currentUserId = (authUser != null) ? authUser.getUserId() : null;
    LeadActivityResponse response = leadService.addActivity(id, request, currentUserId);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{activityId}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  @GetMapping("/{id}/activities")
  @Operation(
      summary = "Consultar historial de actividades",
      description = "Lista todas las actividades e interacciones del lead ordenadas de más reciente a más antigua."
  )
  public ResponseEntity<List<LeadActivityResponse>> getActivities(@PathVariable UUID id) {
    return ResponseEntity.ok(leadService.getActivities(id));
  }
}
