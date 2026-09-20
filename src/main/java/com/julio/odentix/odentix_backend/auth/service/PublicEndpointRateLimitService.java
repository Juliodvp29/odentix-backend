package com.julio.odentix.odentix_backend.auth.service;

import com.julio.odentix.odentix_backend.auth.exception.RateLimitExceededException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Rate limiting genérico por IP para endpoints públicos sin JWT (FASE12-01).
 *
 * <p>El login ya tiene su propio control más estricto ({@link LoginRateLimitService}:
 * por IP + bloqueo por email). Este servicio cubre los demás endpoints públicos que
 * un atacante anónimo puede golpear sin credenciales:
 * <ul>
 *   <li>{@code POST /api/v1/auth/refresh}: fuerza bruta sobre refresh tokens.</li>
 *   <li>{@code POST /api/v1/billing/webhooks/bold}: spam/DoS contra el webhook
 *       (Bold reintenta con backoff, así que el límite es generoso).</li>
 * </ul>
 *
 * <p>Ventana fija de 60 segundos por (endpoint + IP), igual que el login.
 * In-memory con {@link ConcurrentHashMap}: válido para el monolito single-instance
 * en Render; si algún día se escala a varias réplicas habría que externalizar el
 * contador (p. ej. Redis) — no introducir infraestructura antes de que la escala
 * la justifique.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Service
public class PublicEndpointRateLimitService {

  private final int refreshMaxPerMinute;
  private final int webhookMaxPerMinute;

  private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();

  public PublicEndpointRateLimitService(
      @Value("${security.rate-limit.public.refresh.max-requests-per-minute:30}")
      String refreshMaxPerMinute,
      @Value("${security.rate-limit.public.webhook.max-requests-per-minute:120}")
      String webhookMaxPerMinute) {
    // Strings (no primitivos): una env var existente pero vacía no debe tumbar
    // el arranque — se usa el default (mismo patrón que LoginRateLimitService).
    this.refreshMaxPerMinute = enteroOrDefault(refreshMaxPerMinute, 30);
    this.webhookMaxPerMinute = enteroOrDefault(webhookMaxPerMinute, 120);
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

  /**
   * Verifica el límite para {@code POST /api/v1/auth/refresh}.
   *
   * @throws RateLimitExceededException (HTTP 429 + Retry-After) si la IP superó el límite
   */
  public void checkRefreshLimit(String clientIp) {
    check("refresh", clientIp, refreshMaxPerMinute);
  }

  /**
   * Verifica el límite para {@code POST /api/v1/billing/webhooks/bold}.
   *
   * @throws RateLimitExceededException (HTTP 429 + Retry-After) si la IP superó el límite
   */
  public void checkWebhookLimit(String clientIp) {
    check("webhook-bold", clientIp, webhookMaxPerMinute);
  }

  private void check(String endpointKey, String clientIp, int maxPerMinute) {
    if (clientIp == null || clientIp.isBlank()) {
      return;
    }

    String key = endpointKey + "|" + clientIp;
    Instant now = Instant.now();
    trackers.compute(key, (k, tracker) -> {
      if (tracker == null || now.isAfter(tracker.windowStart.plusSeconds(60))) {
        return new Tracker(now, 1);
      }

      if (tracker.count >= maxPerMinute) {
        long elapsedSeconds = Duration.between(tracker.windowStart, now).toSeconds();
        long retryAfter = Math.max(1, 60 - elapsedSeconds);
        throw new RateLimitExceededException(
            "Límite de peticiones excedido para este endpoint público. Intente de nuevo en "
                + retryAfter + " segundos.",
            retryAfter);
      }

      tracker.count++;
      return tracker;
    });
  }

  /**
   * Limpieza periódica cada 5 minutos de ventanas vencidas para evitar acumulación en memoria.
   */
  @Scheduled(fixedRate = 300000)
  public void cleanupExpiredEntries() {
    Instant now = Instant.now();
    trackers.entrySet().removeIf(entry ->
        now.isAfter(entry.getValue().windowStart.plusSeconds(60)));
  }

  /**
   * Limpia todos los registros en memoria (útil para pruebas automatizadas).
   */
  public void reset() {
    trackers.clear();
  }

  private static class Tracker {
    private final Instant windowStart;
    private int count;

    Tracker(Instant windowStart, int count) {
      this.windowStart = windowStart;
      this.count = count;
    }
  }
}

