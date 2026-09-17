package com.julio.odentix.odentix_backend.auth.exception;

/**
 * Excepción lanzada cuando una dirección IP supera el límite de peticiones de login por minuto (HTTP 429).
 */
public class RateLimitExceededException extends RuntimeException {

  private final long retryAfterSeconds;

  public RateLimitExceededException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
