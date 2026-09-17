package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.WaitlistEntryResponse;
import com.julio.odentix.odentix_backend.appointment.service.WaitlistService;
import io.swagger.v3.oas.annotations.Operation;
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
 * Controlador REST para la lista de espera (FASE3-06).
 */
@RestController
@RequestMapping("/api/v1/waitlist")
@Tag(name = "Lista de espera", description = "Registro de pacientes interesados en un horario o procedimiento.")
public class WaitlistController {

  private final WaitlistService waitlistService;

  public WaitlistController(WaitlistService waitlistService) {
    this.waitlistService = waitlistService;
  }

  /**
   * Registra a un paciente en lista de espera.
   *
   * @param request paciente, procedimiento de interés y rango deseado.
   * @return entrada creada con código HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Registrar interesado en lista de espera",
      description = "Crea una entrada en estado activa para un paciente del tenant activo, con procedimiento de interés y rango de fechas deseado opcionales."
  )
  public ResponseEntity<WaitlistEntryResponse> addEntry(
      @Valid @RequestBody CreateWaitlistEntryRequest request) {
    WaitlistEntryResponse response = waitlistService.addEntry(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
