package com.julio.odentix.odentix_backend.specialist.controller;

import com.julio.odentix.odentix_backend.specialist.dto.CreateSpecialistRequest;
import com.julio.odentix.odentix_backend.specialist.dto.SpecialistResponse;
import com.julio.odentix.odentix_backend.specialist.service.SpecialistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para fichas de especialistas externos.
 *
 * <p>La lectura del listado vive en el módulo de liquidaciones
 * ({@code GET /api/v1/specialists}); aquí solo la creación de la ficha
 * financiera, reservada al propietario.
 */
@RestController
@RequestMapping("/api/v1/specialists")
@Tag(name = "Especialistas", description = "Especialistas externos y liquidación de honorarios.")
@SecurityRequirement(name = "bearerAuth")
public class SpecialistController {

  private final SpecialistService specialistService;

  public SpecialistController(SpecialistService specialistService) {
    this.specialistService = specialistService;
  }

  /**
   * Crea la ficha financiera de un especialista externo.
   *
   * <p>El profesional debe existir en el tenant activo y ser externo;
   * un profesional solo puede tener una ficha (409 en el segundo intento).
   * Operación financiera sensible: solo el propietario puede ejecutarla.
   *
   * @param request profesional, porcentaje de honorarios y términos de pago.
   * @return ficha creada con código HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("@subscriptionService.requireFeature('specialists') and hasRole('PROPIETARIO')")
  @Operation(
      summary = "Crear ficha de especialista",
      description = "Pacta el porcentaje de honorarios de un profesional externo. "
          + "Devuelve 409 si el profesional ya tiene ficha y 400 si no es externo."
  )
  public ResponseEntity<SpecialistResponse> crearEspecialista(
      @Valid @RequestBody CreateSpecialistRequest request) {
    SpecialistResponse response = specialistService.crearEspecialista(request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/specialists/{specialistId}/settlements")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
