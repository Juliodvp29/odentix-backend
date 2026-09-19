package com.julio.odentix.odentix_backend.notification.sender;

/**
 * Fallo del proveedor al enviar una notificación (FASE8-03).
 *
 * <p>El servicio la captura y la registra como intento {@code fallida} con su
 * detalle: nunca se propaga al flujo de negocio que originó el envío.
 */
public class NotificationException extends RuntimeException {

  public NotificationException(String message) {
    super(message);
  }

  public NotificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
