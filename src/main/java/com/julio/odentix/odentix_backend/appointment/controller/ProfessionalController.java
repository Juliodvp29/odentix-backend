package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.CreateProfessionalRequest;
import com.julio.odentix.odentix_backend.appointment.dto.ProfessionalResponse;
import com.julio.odentix.odentix_backend.appointment.service.ProfessionalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para profesionales de la clínica.
 *
 * <p>El listado usa los mismos roles que crean citas (recepción necesita el
 * selector); la creación queda reservada al propietario.
 */
@RestController
@RequestMapping("/api/v1/professionals")
@Tag(name = "Profesionales", description = "Profesionales de la clínica (planta y externos).")
@SecurityRequirement(name = "bearerAuth")
public class ProfessionalController {

  private final ProfessionalService professionalService;

  public ProfessionalController(ProfessionalService professionalService) {
    this.professionalService = professionalService;
  }

  /**
   * Lista los profesionales del tenant activo ordenados por nombre.
   *
   * @param onlyActive si es {@code true}, solo profesionales activos.
   * @param externalOnly si es {@code true}, solo especialistas externos.
   * @return profesionales del tenant en orden alfabético.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Listar profesionales",
      description = "Obtiene los profesionales del tenant ordenados por nombre, "
          + "con filtros opcionales por activos y externos."
  )
  public ResponseEntity<List<ProfessionalResponse>> listarProfesionales(
      @RequestParam(required = false) Boolean onlyActive,
      @RequestParam(required = false) Boolean externalOnly) {
    return ResponseEntity.ok(professionalService.listarProfesionales(onlyActive, externalOnly));
  }

  /**
   * Crea un profesional en el tenant activo.
   *
   * <p>Operación administrativa sensible ({@code isExternal} impacta nómina):
   * solo el propietario puede ejecutarla.
   *
   * @param request nombre, especialidad, matrícula y marca de externo.
   * @return profesional creado con código HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(
      summary = "Crear profesional",
      description = "Crea un profesional (de planta o externo) en el tenant activo."
  )
  public ResponseEntity<ProfessionalResponse> crearProfesional(
      @Valid @RequestBody CreateProfessionalRequest request) {
    ProfessionalResponse response = professionalService.crearProfesional(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
