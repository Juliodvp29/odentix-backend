package com.julio.odentix.odentix_backend.subscription.exception;

/**
 * El tenant alcanzó un límite numérico de su plan (FASE11-03).
 *
 * <p>Mapeada a HTTP 429 por `GlobalExceptionHandler`: es cuota agotada, no un
 * error del cliente ni del servidor. Con `retryAfterSeconds` (cuota mensual)
 * se envía cabecera `Retry-After`; en topes de capacidad no aplica.
 */
public class LimitExceededException extends RuntimeException {

  private final Long retryAfterSeconds;

  public LimitExceededException(String message) {
    super(message);
    this.retryAfterSeconds = null;
  }

  public LimitExceededException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
