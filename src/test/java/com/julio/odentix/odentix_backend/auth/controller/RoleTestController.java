package com.julio.odentix.odentix_backend.auth.controller;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de prueba para validar la autorización por rol vía @PreAuthorize (FASE1-11).
 *
 * <p>Ubicado exclusivamente en {@code src/test/} para no desplegarse en producción.
 */
@RestController
@RequestMapping("/api/v1/test-roles")
public class RoleTestController {

  @GetMapping("/propietario")
  @PreAuthorize("hasRole('PROPIETARIO')")
  public ResponseEntity<Map<String, String>> soloPropietario() {
    return ResponseEntity.ok(Map.of("message", "Acceso concedido a propietario"));
  }

  @GetMapping("/cualquiera")
  public ResponseEntity<Map<String, String>> cualquierRolAutenticado() {
    return ResponseEntity.ok(Map.of("message", "Acceso concedido a cualquier rol autenticado"));
  }
}
