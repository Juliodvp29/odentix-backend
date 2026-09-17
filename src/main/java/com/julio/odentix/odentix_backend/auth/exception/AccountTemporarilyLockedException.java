package com.julio.odentix.odentix_backend.auth.exception;

/**
 * Excepción lanzada cuando una cuenta/email es temporalmente bloqueada por exceso de intentos fallidos (HTTP 429).
 */
public class AccountTemporarilyLockedException extends RuntimeException {

  private final long retryAfterSeconds;

  public AccountTemporarilyLockedException(String message, long retryAfterSeconds) {
    super(message);
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
