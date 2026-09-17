package com.julio.odentix.odentix_backend.shared.storage.exception;

/**
 * Excepción lanzada cuando ocurre un error de comunicación u operación con el almacenamiento (FASE2-09).
 */
public class StorageException extends RuntimeException {

  public StorageException(String message) {
    super(message);
  }

  public StorageException(String message, Throwable cause) {
    super(message, cause);
  }
}
