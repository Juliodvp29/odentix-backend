package com.julio.odentix.odentix_backend.assistant.controller;

import com.julio.odentix.odentix_backend.assistant.dto.AskRequest;
import com.julio.odentix.odentix_backend.assistant.dto.AskResponse;
import com.julio.odentix.odentix_backend.assistant.service.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST del asistente administrativo (FASE10-01).
 *
 * <p>Solo lectura sobre datos del tenant: roles operativos amplios. Sin API
 * key del proveedor responde 502 (el fallback a plantilla fija llega en
 * FASE10-03).
 */
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "Asistente", description = "Asistente administrativo con IA sobre datos de la clínica.")
@SecurityRequirement(name = "bearerAuth")
public class AssistantController {

  private final AssistantService assistantService;

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  /**
   * Responde una pregunta en lenguaje natural con datos reales del tenant.
   */
  @PostMapping("/ask")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Preguntar al asistente",
      description = "Responde con datos reales de la clínica (oportunidades, planes, "
          + "citas, cartera, inventario, leads). Nunca mezcla datos de otra clínica."
  )
  public ResponseEntity<AskResponse> ask(@Valid @RequestBody AskRequest request) {
    return ResponseEntity.ok(assistantService.ask(request));
  }
}
