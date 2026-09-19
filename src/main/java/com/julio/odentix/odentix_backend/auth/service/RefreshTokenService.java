package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.entity.RefreshToken;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio para gestión del ciclo de vida de Refresh Tokens (FASE1-IMPROVE).
 *
 * <p>Maneja la generación de tokens criptográficamente seguros, almacenamiento de su hash
 * SHA-256 en base de datos, detección de reúso, rotación atómica en cada refresco y revocación en logout.
 *
 * <p>Convención: Sin Lombok (regla §9 de AGENTS.md).
 */
@Service
public class RefreshTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final RefreshTokenRepository refreshTokenRepository;
  private final long refreshTokenExpirationDays;

  public RefreshTokenService(
      RefreshTokenRepository refreshTokenRepository,
      @Value("${jwt.refresh-token-expiration-days:7}") String refreshTokenExpirationDays) {
    this.refreshTokenRepository = refreshTokenRepository;
    // String (no long): una env var existente pero vacía no debe tumbar el
    // arranque —se usa el default (diagnosticado 2026-09-19).
    long dias;
    try {
      dias = refreshTokenExpirationDays != null && !refreshTokenExpirationDays.isBlank()
          ? Long.parseLong(refreshTokenExpirationDays.strip())
          : 7L;
    } catch (NumberFormatException e) {
      dias = 7L;
    }
    this.refreshTokenExpirationDays = dias;
  }

  /**
   * Genera un nuevo refresh token, guarda su hash SHA-256 en BD y retorna el token en texto plano.
   */
  @Transactional
  public String createRefreshToken(User user) {
    String rawToken = generateSecureToken();
    String tokenHash = hashToken(rawToken);
    Instant expiresAt = Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS);

    RefreshToken refreshToken = new RefreshToken(user.getTenant(), user, tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    return rawToken;
  }

  /**
   * Resultado de rotar un refresh token: usuario asociado y nuevo token en texto plano.
   */
  public record RotatedTokenResult(User user, String newRefreshToken) {
  }

  /**
   * Valida y rota un refresh token existente.
   *
   * <p>Si el token ya fue revocado, detecta reúso indebido y rechaza la petición.
   * Si es válido y vigente, marca el token actual como revocado, genera uno nuevo y lo persiste.
   *
   * @param rawRefreshToken token recibido en texto plano
   * @return RotatedTokenResult con el usuario y el nuevo refresh token
   * @throws BadCredentialsException si el token es inexistente, expirado, revocado o el usuario está inactivo
   */
  @Transactional
  public RotatedTokenResult rotateRefreshToken(String rawRefreshToken) {
    String tokenHash = hashToken(rawRefreshToken);
    RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

    if (token.isRevoked()) {
      throw new BadCredentialsException("Refresh token revocado");
    }

    if (token.getExpiresAt().isBefore(Instant.now())) {
      throw new BadCredentialsException("Refresh token expirado");
    }

    User user = token.getUser();
    if (user == null || !user.isActive()) {
      throw new BadCredentialsException("Usuario inactivo");
    }

    // Revocar token actual
    token.setRevoked(true);
    token.setRevokedAt(Instant.now());
    refreshTokenRepository.save(token);

    // Emitir nuevo refresh token rotado
    String newRawToken = generateSecureToken();
    String newTokenHash = hashToken(newRawToken);
    Instant newExpiresAt = Instant.now().plus(refreshTokenExpirationDays, ChronoUnit.DAYS);

    RefreshToken newRefreshToken = new RefreshToken(user.getTenant(), user, newTokenHash, newExpiresAt);
    refreshTokenRepository.save(newRefreshToken);

    return new RotatedTokenResult(user, newRawToken);
  }

  /**
   * Revoca un refresh token explícitamente (ej. logout).
   *
   * @param rawRefreshToken token recibido en texto plano
   * @throws BadCredentialsException si el token no existe
   */
  @Transactional
  public void revokeToken(String rawRefreshToken) {
    String tokenHash = hashToken(rawRefreshToken);
    RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
        .orElseThrow(() -> new BadCredentialsException("Refresh token inválido"));

    if (!token.isRevoked()) {
      token.setRevoked(true);
      token.setRevokedAt(Instant.now());
      refreshTokenRepository.save(token);
    }
  }

  private String generateSecureToken() {
    byte[] randomBytes = new byte[32];
    SECURE_RANDOM.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  /**
   * Calcula el hash SHA-256 en formato hexadecimal.
   */
  public static String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Error al inicializar algoritmo SHA-256", e);
    }
  }
}
