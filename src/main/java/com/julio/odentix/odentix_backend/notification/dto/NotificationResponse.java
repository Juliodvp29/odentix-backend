package com.julio.odentix.odentix_backend.notification.dto;

import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Intento de notificación para la vista de estado (lectura de depuración).
 *
 * <p>Sin el cuerpo del mensaje ({@code payload}): para diagnosticar un
 * envío fallido bastan el canal, el destinatario, la plantilla y el
 * detalle del error. Sin Lombok.
 */
public class NotificationResponse {

  private UUID id;
  private UUID patientId;
  private NotificationChannel channel;
  private String recipient;
  private String templateKey;
  private NotificationStatus status;
  private Instant sentAt;
  private String errorDetail;
  private Instant createdAt;

  public NotificationResponse() {
  }

  public static NotificationResponse fromEntity(Notification notification) {
    NotificationResponse response = new NotificationResponse();
    response.setId(notification.getId());
    response.setPatientId(notification.getPatientId());
    response.setChannel(notification.getChannel());
    response.setRecipient(notification.getRecipient());
    response.setTemplateKey(notification.getTemplateKey());
    response.setStatus(notification.getStatus());
    response.setSentAt(notification.getSentAt());
    response.setErrorDetail(notification.getErrorDetail());
    response.setCreatedAt(notification.getCreatedAt());
    return response;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public void setChannel(NotificationChannel channel) {
    this.channel = channel;
  }

  public String getRecipient() {
    return recipient;
  }

  public void setRecipient(String recipient) {
    this.recipient = recipient;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public void setSentAt(Instant sentAt) {
    this.sentAt = sentAt;
  }

  public String getErrorDetail() {
    return errorDetail;
  }

  public void setErrorDetail(String errorDetail) {
    this.errorDetail = errorDetail;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
