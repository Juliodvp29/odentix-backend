package com.julio.odentix.odentix_backend.notification.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

/**
 * Registro de un intento de notificación (FASE8-03).
 *
 * <p>Cada envío por cualquier canal deja una fila con su resultado: el fallo
 * de un proveedor (SMTP caído, WhatsApp caído en FASE8-05) se registra aquí
 * con {@code fallida} + {@code error_detail} y nunca revierte la operación
 * de negocio que lo originó.
 *
 * <p>El paciente se modela como UUID simple (precedente
 * {@code ClinicalRecord.professionalId}): una notificación puede no tener
 * paciente asociado (ej. aviso interno).
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "notifications")
public class Notification extends TenantAwareEntity {

  @Column(name = "patient_id")
  private UUID patientId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "channel", nullable = false)
  private NotificationChannel channel;

  @Column(name = "recipient", nullable = false, columnDefinition = "TEXT")
  private String recipient;

  @Column(name = "template_key")
  private String templateKey;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> payload;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private NotificationStatus status = NotificationStatus.pendiente;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "error_detail", columnDefinition = "TEXT")
  private String errorDetail;

  public Notification() {
    super();
  }

  public Notification(UUID tenantId, NotificationChannel channel, String recipient) {
    super(tenantId);
    this.channel = channel;
    this.recipient = recipient;
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

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public void setStatus(NotificationStatus status) {
    this.status = status != null ? status : NotificationStatus.pendiente;
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

  @Override
  public String toString() {
    // Sin destinatario en logs (dato personal del paciente).
    return "Notification{"
        + "id=" + getId()
        + ", channel=" + channel
        + ", status=" + status
        + '}';
  }
}
