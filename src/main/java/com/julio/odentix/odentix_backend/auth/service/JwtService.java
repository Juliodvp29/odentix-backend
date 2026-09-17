package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emisión, firma y validación de JSON Web Tokens (FASE1-06).
 *
 * <p>Los tokens emitidos contienen los claims mínimos requeridos por el sistema multi-tenant:
 * sub (user_id), tenant_id y role.
 *
 * <p>Convención: Sin Lombok (regla §9 de AGENTS.md).
 */
@Service
public class JwtService {

  private final SecretKey secretKey;
  private final long expirationMinutes;

  public JwtService(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration-minutes:1440}") long expirationMinutes) {
    // Convierte el secreto a clave HMAC-SHA256 (mínimo 256 bits / 32 bytes)
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMinutes = expirationMinutes;
  }

  /**
   * Genera un JWT firmado con los claims de identidad y multi-tenancy del usuario.
   */
  public String generateToken(User user) {
    Instant now = Instant.now();
    Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);
    return generateToken(user, Date.from(now), Date.from(expiry));
  }

  /**
   * Genera un JWT con fechas explícitas de emisión y expiración.
   */
  public String generateToken(User user, Date issuedAt, Date expiration) {
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("tenant_id", user.getTenant().getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .issuedAt(issuedAt)
        .expiration(expiration)
        .signWith(secretKey)
        .compact();
  }

  /**
   * Parsea y valida la firma del token, retornando todos sus claims.
   *
   * @throws JwtException si la firma es inválida, está expirado o fue alterado
   */
  public Claims extractAllClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  /**
   * Valida detalladamente el token distinguiendo entre vigente, expirado o inválido/manipulado.
   */
  public JwtValidationResult validateTokenResult(String token) {
    try {
      Claims claims = extractAllClaims(token);
      if (claims.getExpiration() != null && claims.getExpiration().before(new Date())) {
        return JwtValidationResult.EXPIRED;
      }
      return JwtValidationResult.VALID;
    } catch (ExpiredJwtException e) {
      return JwtValidationResult.EXPIRED;
    } catch (JwtException | IllegalArgumentException e) {
      return JwtValidationResult.INVALID;
    }
  }

  /**
   * Valida si el token es estructuralmente correcto, su firma es válida y no ha expirado.
   */
  public boolean validateToken(String token) {
    return validateTokenResult(token) == JwtValidationResult.VALID;
  }

  public UUID extractUserId(String token) {
    return UUID.fromString(extractAllClaims(token).getSubject());
  }

  public UUID extractTenantId(String token) {
    String tenantIdStr = extractAllClaims(token).get("tenant_id", String.class);
    return UUID.fromString(tenantIdStr);
  }

  public String extractEmail(String token) {
    return extractAllClaims(token).get("email", String.class);
  }

  public String extractRole(String token) {
    return extractAllClaims(token).get("role", String.class);
  }

  public long getExpirationMinutes() {
    return expirationMinutes;
  }
}
