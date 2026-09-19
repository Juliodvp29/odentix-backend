package com.julio.odentix.odentix_backend.assistant;

/**
 * Fallo del proveedor de IA (FASE10-01).
 *
 * <p>Se mapea a 502 Bad Gateway: el backend está bien, el que falló fue el
 * proveedor externo. El fallback a plantillas fijas llega en FASE10-03.
 */
public class AssistantException extends RuntimeException {

  public AssistantException(String message) {
    super(message);
  }

  public AssistantException(String message, Throwable cause) {
    super(message, cause);
  }
}
