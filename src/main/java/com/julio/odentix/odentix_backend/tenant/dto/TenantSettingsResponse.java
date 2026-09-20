package com.julio.odentix.odentix_backend.tenant.dto;

import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import java.util.UUID;

/**
 * Ajustes actuales de la clínica (pre-Fase 12). Sin Lombok.
 */
public class TenantSettingsResponse {

  private UUID tenantId;
  private String name;
  private String notificationEmail;
  private String notificationName;
  private String whatsappPhoneNumberId;
  private boolean whatsappConfigured;

  public TenantSettingsResponse() {
  }

  public static TenantSettingsResponse fromEntity(Tenant tenant) {
    TenantSettingsResponse dto = new TenantSettingsResponse();
    dto.tenantId = tenant.getId();
    dto.name = tenant.getName();
    dto.notificationEmail = tenant.getNotificationEmail();
    dto.notificationName = tenant.getNotificationName();
    dto.whatsappPhoneNumberId = tenant.getWhatsappPhoneNumberId();
    // El token jamás se devuelve (write-only): solo si hay credencial completa.
    dto.whatsappConfigured = tenant.getWhatsappPhoneNumberId() != null
        && !tenant.getWhatsappPhoneNumberId().isBlank()
        && tenant.getWhatsappTokenCifrado() != null
        && !tenant.getWhatsappTokenCifrado().isBlank();
    return dto;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public boolean isWhatsappConfigured() {
    return whatsappConfigured;
  }

  public void setWhatsappConfigured(boolean whatsappConfigured) {
    this.whatsappConfigured = whatsappConfigured;
  }
}

