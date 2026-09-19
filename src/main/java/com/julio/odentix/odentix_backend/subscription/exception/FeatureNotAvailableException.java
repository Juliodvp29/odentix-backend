package com.julio.odentix.odentix_backend.subscription.exception;

/**
 * El plan activo del tenant no incluye el feature requerido (FASE11-02).
 *
 * <p>Mapeada a HTTP 403 por `GlobalExceptionHandler`, con mensaje orientado a
 * upgrade (no un 403 genérico de permisos).
 */
public class FeatureNotAvailableException extends RuntimeException {

  public FeatureNotAvailableException(String featureKey, String planCode) {
    super("Tu plan actual (" + planCode + ") no incluye '" + featureKey
        + "'. Sube de plan para usar esta funcionalidad.");
  }
}
