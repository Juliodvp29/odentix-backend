package com.julio.odentix.odentix_backend.notification.entity;

/**
 * Estados de un intento de notificación (FASE8-03).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * {@code notification_status}: 'pendiente', 'enviada', 'fallida'. Un fallo
 * nunca bloquea el flujo que originó la notificación: queda registrado como
 * {@code fallida} con su {@code error_detail}.
 */
public enum NotificationStatus {
  pendiente,
  enviada,
  fallida
}
