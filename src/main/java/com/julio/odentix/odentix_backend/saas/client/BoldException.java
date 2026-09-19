package com.julio.odentix.odentix_backend.saas.client;

/**
 * Fallo llamando a Bold (FASE11-04): sin key, timeout o rechazo.
 */
public class BoldException extends RuntimeException {

  public BoldException(String message) {
    super(message);
  }

  public BoldException(String message, Throwable cause) {
    super(message, cause);
  }
}
