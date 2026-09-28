package com.julio.odentix.odentix_backend.auth.controller;

import com.julio.odentix.odentix_backend.auth.dto.UserSummaryDto;
import com.julio.odentix.odentix_backend.auth.service.UserService;
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
 * Listado de miembros del tenant activo.
 *
 * <p>El tenant sale del token, nunca de un parámetro. Reutiliza
 * {@link UserSummaryDto}, que ya lleva lo que el frontend necesita.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Usuarios", description = "Miembros del tenant activo.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  /**
   * Lista los miembros del tenant activo ordenados por nombre.
   *
   * @return usuarios del tenant del token en orden alfabético.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO','ODONTOLOGO','RECEPCION','AUXILIAR')")
  @Operation(
      summary = "Listar usuarios",
      description = "Obtiene los miembros del tenant activo ordenados por nombre."
  )
  public ResponseEntity<List<UserSummaryDto>> listarUsuarios() {
    return ResponseEntity.ok(userService.listarUsuarios());
  }
}
