package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.RoomResponse;
import com.julio.odentix.odentix_backend.appointment.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para consultorios de la clínica.
 *
 * <p>El listado usa los mismos roles que crean citas (recepción necesita el
 * selector de agenda).
 */
@RestController
@RequestMapping("/api/v1/rooms")
@Tag(name = "Consultorios", description = "Consultorios o espacios de atención de la clínica.")
@SecurityRequirement(name = "bearerAuth")
public class RoomController {

  private final RoomService roomService;

  public RoomController(RoomService roomService) {
    this.roomService = roomService;
  }

  /**
   * Lista los consultorios del tenant activo ordenados por nombre.
   *
   * @return consultorios del tenant en orden alfabético.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Listar consultorios",
      description = "Obtiene los consultorios del tenant ordenados por nombre."
  )
  public ResponseEntity<List<RoomResponse>> listarConsultorios() {
    return ResponseEntity.ok(roomService.listarConsultorios());
  }
}
