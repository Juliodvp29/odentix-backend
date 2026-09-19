package com.julio.odentix.odentix_backend.notification.entity;

/**
 * Canales de notificación (FASE8-03).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL
 * {@code notification_channel}: 'email', 'whatsapp', 'sms'. El adaptador de
 * WhatsApp llega en FASE8-05.
 */
public enum NotificationChannel {
  email,
  whatsapp,
  sms
}
