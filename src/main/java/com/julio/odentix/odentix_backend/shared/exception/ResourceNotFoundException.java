package com.julio.odentix.odentix_backend.shared.exception;

/**
 * Excepción estándar lanzada cuando un recurso de negocio no existe dentro del tenant
 * del usuario autenticado (o no existe en absoluto).
 *
 * <p>Mapeada a HTTP 404 por {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  public ResourceNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
