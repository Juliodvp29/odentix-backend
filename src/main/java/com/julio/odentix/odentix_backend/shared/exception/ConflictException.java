package com.julio.odentix.odentix_backend.shared.exception;

/**
 * Excepción estándar lanzada cuando una operación viola una regla de negocio
 * que constituye un conflicto de estado (HTTP 409 Conflict).
 *
 * <p>Ejemplos: crear un plan de pago para un tratamiento que ya tiene uno,
 * intentar pagar una cuota que ya fue pagada, transiciones de estado inválidas.
 *
 * <p>Mapeada a HTTP 409 por {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }

  public ConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
