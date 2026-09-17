package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.audit.entity.AuditAction;
import com.julio.odentix.odentix_backend.audit.service.AuditService;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.dto.LoginResponse;
import com.julio.odentix.odentix_backend.auth.dto.UserSummaryDto;
import com.julio.odentix.odentix_backend.auth.dto.LogoutRequest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshRequest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshResponse;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.RefreshTokenService.RotatedTokenResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de autenticación y emisión de credenciales (FASE1-06 / FASE1-IMPROVE).
 *
 * <p>Maneja el flujo de login por email y contraseña, rotación de refresh tokens y logout.
 * No filtra detalles del fallo (usuario inexistente vs contraseña errónea) para prevenir ataques de enumeración.
 *
 * <p>Convención: Sin Lombok (regla §9 de AGENTS.md).
 */
@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final RefreshTokenService refreshTokenService;
  private final AuditService auditService;

  public AuthService(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtService jwtService,
      RefreshTokenService refreshTokenService,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.refreshTokenService = refreshTokenService;
    this.auditService = auditService;
  }

  /**
   * Autentica las credenciales provistas y retorna el JWT firmado con claims de tenant y rol.
   *
   * @throws BadCredentialsException si el usuario no existe, la contraseña no coincide o está inactivo
   */
  @Transactional
  public LoginResponse login(LoginRequest request) {
    User user = buscarCandidato(request);

    if (user == null || !user.isActive()
        || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
      registrarFallo(request, user);
      throw new BadCredentialsException("Credenciales inválidas");
    }

    // Registro de auditoría básica: actualizar fecha del último login
    user.setLastLoginAt(Instant.now());
    userRepository.save(user);

    // Primer caso de uso real de la auditoría (FASE1-14). Solo el email en el
    // detalle: nunca la contraseña, ni siquiera la fallida.
    auditService.log(
        user.getTenant().getId(),
        user.getId(),
        AuditAction.login_success,
        "users",
        user.getId(),
        Map.of("email", request.getEmail()));

    String token = jwtService.generateToken(user);
    String refreshToken = refreshTokenService.createRefreshToken(user);
    long expiresInSeconds = jwtService.getExpirationMinutes() * 60;

    return new LoginResponse(token, refreshToken, expiresInSeconds, UserSummaryDto.fromEntity(user));
  }

  /**
   * Rota el refresh token provisto y emite un nuevo par (access token + refresh token).
   */
  @Transactional
  public TokenRefreshResponse refreshToken(TokenRefreshRequest request) {
    RotatedTokenResult result = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
    String newAccessToken = jwtService.generateToken(result.user());
    long expiresInSeconds = jwtService.getExpirationMinutes() * 60;

    return new TokenRefreshResponse(newAccessToken, result.newRefreshToken(), expiresInSeconds);
  }

  /**
   * Revoca el refresh token para cerrar la sesión de forma segura.
   */
  @Transactional
  public void logout(LogoutRequest request) {
    refreshTokenService.revokeToken(request.getRefreshToken());
  }

  private User buscarCandidato(LoginRequest request) {
    if (request.getTenantId() != null) {
      return userRepository.findByTenantIdAndEmail(request.getTenantId(), request.getEmail())
          .orElse(null);
    }
    List<User> users = userRepository.findByEmail(request.getEmail());
    if (users.size() != 1) {
      // Cero (email inexistente) o más de uno (colisión entre clínicas sin
      // desambiguar): en ambos casos no hay un candidato único.
      return null;
    }
    return users.get(0);
  }

  /**
   * Registra un login fallido solo si el tenant es atribuible (FASE1-14).
   *
   * <p>Si se encontró al usuario (o el request trae tenantId aunque el email no
   * exista), el fallo se atribuye a ese tenant. Si no hay tenant atribuible,
   * no se registra nada: inventar un tenant violaría el aislamiento y
   * {@code tenant_id} es NOT NULL. La respuesta sigue siendo 401 genérica.
   */
  private void registrarFallo(LoginRequest request, User user) {
    UUID tenantId = user != null ? user.getTenant().getId() : request.getTenantId();
    UUID userId = user != null ? user.getId() : null;
    if (tenantId == null) {
      return;
    }
    auditService.log(
        tenantId, userId, AuditAction.login_failed, "users", userId, Map.of("email", request.getEmail()));
  }
}
