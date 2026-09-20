package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.exception.AccountTemporarilyLockedException;
import com.julio.odentix.odentix_backend.auth.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Servicio en memoria para Rate Limiting y prevención de ataques de fuerza bruta en login.
 *
 * <p>Aplica dos capas de defensa en profundidad:
 * <ul>
 *   <li><b>Rate limiting por IP:</b> Máximo N peticiones por minuto por IP hacia /api/v1/auth/login.
 *       Evita saturación de CPU por cálculo intensivo de BCrypt y ataques DoS.</li>
 *   <li><b>Bloqueo temporal por cuenta/email:</b> Máximo M fallos consecutivos en ventana de tiempo.
 *       Al superar el umbral, bloquea temporalmente los intentos para ese email durante K minutos.</li>
 * </ul>
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Service
public class LoginRateLimitService {

  private final int maxRequestsPerMinute;
  private final int maxFailedAttempts;
  private final long lockoutDurationMinutes;

  private final Map<String, IpTracker> ipTrackers = new ConcurrentHashMap<>();
  private final Map<String, AccountLockTracker> emailTrackers = new ConcurrentHashMap<>();

  public LoginRateLimitService(
      @Value("${security.rate-limit.login.max-requests-per-minute:10}") String maxRequestsPerMinute,
      @Value("${security.rate-limit.login.max-failed-attempts:5}") String maxFailedAttempts,
      @Value("${security.rate-limit.login.lockout-duration-minutes:15}")
      String lockoutDurationMinutes) {
    // Strings (no primitivos): una env var existente pero vacía no debe tumbar
    // el arranque —se usa el default (diagnosticado 2026-09-19).
    this.maxRequestsPerMinute = enteroOrDefault(maxRequestsPerMinute, 10);
    this.maxFailedAttempts = enteroOrDefault(maxFailedAttempts, 5);
    this.lockoutDurationMinutes = largoOrDefault(lockoutDurationMinutes, 15L);
  }

  private static int enteroOrDefault(String valor, int defecto) {
    if (valor == null || valor.isBlank()) {
      return defecto;
    }
    try {
      return Integer.parseInt(valor.strip());
    } catch (NumberFormatException e) {
      return defecto;
    }
  }

  private static long largoOrDefault(String valor, long defecto) {
    if (valor == null || valor.isBlank()) {
      return defecto;
    }
    try {
      return Long.parseLong(valor.strip());
    } catch (NumberFormatException e) {
      return defecto;
    }
  }

  /**
   * Verifica si la IP cliente tiene permitido realizar una solicitud de login en la ventana actual.
   *
   * @throws RateLimitExceededException si superó el límite de peticiones por minuto
   */
  public void checkIpRateLimit(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }

    Instant now = Instant.now();
    ipTrackers.compute(clientIp, (ip, tracker) -> {
      if (tracker == null || now.isAfter(tracker.windowStart.plusSeconds(60))) {
        return new IpTracker(now, 1);
      }

      if (tracker.count >= maxRequestsPerMinute) {
        long elapsedSeconds = Duration.between(tracker.windowStart, now).toSeconds();
        long retryAfter = Math.max(1, 60 - elapsedSeconds);
        throw new RateLimitExceededException(
            "Límite de peticiones de login excedido para esta dirección IP. Intente de nuevo en "
                + retryAfter + " segundos.",
            retryAfter);
      }

      tracker.count++;
      return tracker;
    });
  }

  /**
   * Verifica si el email provisto no se encuentra temporalmente bloqueado por intentos fallidos previos.
   *
   * @throws AccountTemporarilyLockedException si la cuenta está actualmente bloqueada
   */
  public void checkEmailLockout(String email) {
    if (email == null || email.isBlank()) {
      return;
    }

    String key = email.toLowerCase().trim();
    AccountLockTracker tracker = emailTrackers.get(key);
    if (tracker == null) {
      return;
    }

    Instant now = Instant.now();
    if (tracker.lockedUntil != null) {
      if (tracker.lockedUntil.isAfter(now)) {
        long retryAfter = Math.max(1, Duration.between(now, tracker.lockedUntil).toSeconds());
        throw new AccountTemporarilyLockedException(
            "La cuenta se encuentra temporalmente bloqueada por exceso de intentos fallidos. Intente de nuevo en "
                + retryAfter + " segundos.",
            retryAfter);
      } else {
        // Bloqueo expirado: resetear estado
        emailTrackers.remove(key);
      }
    }
  }

  /**
   * Registra un intento de login fallido para el email correspondiente.
   * Si alcanza el número máximo de fallos, activa el bloqueo temporal.
   */
  public void recordFailedAttempt(String email) {
    if (email == null || email.isBlank()) {
      return;
    }

    String key = email.toLowerCase().trim();
    Instant now = Instant.now();

    emailTrackers.compute(key, (k, tracker) -> {
      if (tracker == null || now.isAfter(tracker.windowStart.plus(lockoutDurationMinutes, ChronoUnit.MINUTES))) {
        return new AccountLockTracker(1, now, null);
      }

      tracker.failedAttempts++;
      if (tracker.failedAttempts >= maxFailedAttempts) {
        tracker.lockedUntil = now.plus(lockoutDurationMinutes, ChronoUnit.MINUTES);
      }
      return tracker;
    });
  }

  /**
   * Registra un inicio de sesión exitoso, limpiando el contador de intentos fallidos del email.
   */
  public void recordSuccessfulLogin(String email) {
    if (email != null && !email.isBlank()) {
      emailTrackers.remove(email.toLowerCase().trim());
    }
  }

  /**
   * Limpieza periódica cada 5 minutos de registros vencidos para evitar acumulación en memoria.
   */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredEntries() {
    Instant now = Instant.now();

    ipTrackers.entrySet().removeIf(entry ->
        now.isAfter(entry.getValue().windowStart.plusSeconds(60)));

    emailTrackers.entrySet().removeIf(entry -> {
      AccountLockTracker tracker = entry.getValue();
      if (tracker.lockedUntil != null) {
        return now.isAfter(tracker.lockedUntil);
      }
      return now.isAfter(tracker.windowStart.plus(lockoutDurationMinutes, ChronoUnit.MINUTES));
    });
  }

  /**
   * Limpia todos los registros en memoria (útil para pruebas automatizadas).
   */
  public void reset() {
    ipTrackers.clear();
    emailTrackers.clear();
  }

  private static class IpTracker {
    private final Instant windowStart;
    private int count;

    public IpTracker(Instant windowStart, int count) {
      this.windowStart = windowStart;
      this.count = count;
    }
  }

  private static class AccountLockTracker {
    private int failedAttempts;
    private final Instant windowStart;
    private Instant lockedUntil;

    public AccountLockTracker(int failedAttempts, Instant windowStart, Instant lockedUntil) {
      this.failedAttempts = failedAttempts;
      this.windowStart = windowStart;
      this.lockedUntil = lockedUntil;
    }
  }
}

