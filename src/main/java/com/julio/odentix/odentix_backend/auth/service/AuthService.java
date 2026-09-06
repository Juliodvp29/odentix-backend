package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.dto.LoginResponse;
import com.julio.odentix.odentix_backend.auth.dto.UserSummaryDto;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticación y emisión de credenciales (FASE1-06).
 *
 * <p>Maneja el flujo de login por email y contraseña. No filtra detalles del fallo
 * (usuario inexistente vs contraseña errónea) para prevenir ataques de enumeración.
 *
 * <p>Convención: Sin Lombok (regla §9 de AGENTS.md).
 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  /**
   * Autentica las credenciales provistas y retorna el JWT firmado con claims de tenant y rol.
   *
   * @throws BadCredentialsException si el usuario no existe, la contraseña no coincide o está inactivo
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user;
    if (request.getTenantId() != null) {
      user = userRepository.findByTenantIdAndEmail(request.getTenantId(), request.getEmail())
          .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
    } else {
      List<User> users = userRepository.findByEmail(request.getEmail());
      if (users.isEmpty()) {
        throw new BadCredentialsException("Credenciales inválidas");
      }
      if (users.size() > 1) {
        // En caso de colisión de email entre dos clínicas distintas, se requiere desambiguar
        throw new BadCredentialsException("Credenciales inválidas");
      }
      user = users.get(0);
    }

    if (!user.isActive()) {
      throw new BadCredentialsException("Credenciales inválidas");
    }

    if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      throw new BadCredentialsException("Credenciales inválidas");
    }

    // Registro de auditoría básica: actualizar fecha del último login
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);

    String token = jwtService.generateToken(user);
    long expiresInSeconds = jwtService.getExpirationMinutes() * 60;

    return new LoginResponse(token, expiresInSeconds, UserSummaryDto.fromEntity(user));
  }
}
