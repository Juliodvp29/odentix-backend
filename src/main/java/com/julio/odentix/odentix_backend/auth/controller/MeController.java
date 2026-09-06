package com.julio.odentix.odentix_backend.auth.controller;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.auth.dto.UserSummaryDto;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Endpoint protegido para consultar el perfil del usuario autenticado (FASE1-07).
 */
@RestController
@RequestMapping("/api/v1")
public class MeController {

  private final UserRepository userRepository;

  public MeController(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @GetMapping("/me")
  public ResponseEntity<UserSummaryDto> getCurrentUser(
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    if (authUser == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
    }

    User user = userRepository.findById(authUser.getUserId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

    return ResponseEntity.ok(UserSummaryDto.fromEntity(user));
  }
}
