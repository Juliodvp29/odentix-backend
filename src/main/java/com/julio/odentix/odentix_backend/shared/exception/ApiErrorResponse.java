package com.julio.odentix.odentix_backend.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Estructura de error estándar para todas las respuestas de error de la API (FASE2-03).
 *
 * <p>Provee un formato consistente y predecible para el cliente (Angular / API consumers)
 * sin filtrar detalles internos de la base de datos ni stacktraces.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

  private final Instant timestamp;
  private final int status;
  private final String error;
  private final String message;
  private final String path;
  private final Map<String, String> errors;

  public ApiErrorResponse(int status, String error, String message, String path) {
    this(Instant.now(), status, error, message, path, null);
  }

  public ApiErrorResponse(
      int status, String error, String message, String path, Map<String, String> errors) {
    this(Instant.now(), status, error, message, path, errors);
  }

  public ApiErrorResponse(
      Instant timestamp,
      int status,
      String error,
      String message,
      String path,
      Map<String, String> errors) {
    this.timestamp = timestamp;
    this.status = status;
    this.error = error;
    this.message = message;
    this.path = path;
    this.errors = errors;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public int getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public String getMessage() {
    return message;
  }

  public String getPath() {
    return path;
  }

  public Map<String, String> getErrors() {
    return errors;
  }
}
