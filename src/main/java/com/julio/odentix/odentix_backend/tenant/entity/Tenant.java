package com.julio.odentix.odentix_backend.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Entidad raíz del modelo multi-tenant (FASE1-01).
 *
 * <p>Representa una clínica odontológica cliente. Toda información de negocio
 * pertenece directa o indirectamente a un {@code Tenant}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "tenants")
public class Tenant {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "tax_id")
  private String taxId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private TenantStatus status = TenantStatus.trial;

  @Column(nullable = false)
  private String timezone = "America/Bogota";

  @Column(name = "notification_email")
  private String notificationEmail;

  @Column(name = "notification_name")
  private String notificationName;

  @Column(name = "whatsapp_phone_number_id")
  private String whatsappPhoneNumberId;

  @Column(name = "whatsapp_token_cifrado", columnDefinition = "TEXT")
  private String whatsappTokenCifrado;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Tenant() {
  }

  public Tenant(String name, String taxId) {
    this.name = name;
    this.taxId = taxId;
    this.status = TenantStatus.trial;
    this.timezone = "America/Bogota";
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
    if (this.status == null) {
      this.status = TenantStatus.trial;
    }
    if (this.timezone == null) {
      this.timezone = "America/Bogota";
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTaxId() {
    return taxId;
  }

  public void setTaxId(String taxId) {
    this.taxId = taxId;
  }

  public TenantStatus getStatus() {
    return status;
  }

  public void setStatus(TenantStatus status) {
    this.status = status;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public String getNotificationEmail() {
    return notificationEmail;
  }

  public void setNotificationEmail(String notificationEmail) {
    this.notificationEmail = notificationEmail;
  }

  public String getNotificationName() {
    return notificationName;
  }

  public void setNotificationName(String notificationName) {
    this.notificationName = notificationName;
  }

  public String getWhatsappPhoneNumberId() {
    return whatsappPhoneNumberId;
  }

  public void setWhatsappPhoneNumberId(String whatsappPhoneNumberId) {
    this.whatsappPhoneNumberId = whatsappPhoneNumberId;
  }

  public String getWhatsappTokenCifrado() {
    return whatsappTokenCifrado;
  }

  public void setWhatsappTokenCifrado(String whatsappTokenCifrado) {
    this.whatsappTokenCifrado = whatsappTokenCifrado;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Tenant tenant = (Tenant) o;
    return id != null && Objects.equals(id, tenant.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "Tenant{" +
        "id=" + id +
        ", name='" + name + '\'' +
        ", taxId='" + taxId + '\'' +
        ", status=" + status +
        ", timezone='" + timezone + '\'' +
        '}';
  }
}

