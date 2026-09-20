package com.julio.odentix.odentix_backend.tenant.dto;

import jakarta.validation.constraints.Size;

/**
 * Ajustes editables de la propia clínica (pre-Fase 12).
 *
 * <p>`notificationEmail` vacío/nulo limpia el valor y vuelve al remitente
 * global. El formato se valida en el servicio (la anotación `@Email` no
 * admite blancos y bloquearía el limpiado). Sin Lombok.
 */
public class UpdateTenantSettingsRequest {

  @Size(max = 255, message = "notificationEmail no puede superar 255 caracteres")
  private String notificationEmail;

  @Size(max = 255, message = "notificationName no puede superar 255 caracteres")
  private String notificationName;

  @Size(max = 30, message = "whatsappPhoneNumberId no puede superar 30 caracteres")
  private String whatsappPhoneNumberId;

  /**
   * Token de Meta en claro (solo escritura): se cifra antes de guardar y jamás
   * se devuelve. Vacío/nulo lo deja intacto; para rotarlo se envía el nuevo.
   */
  @Size(max = 500, message = "whatsappToken no puede superar 500 caracteres")
  private String whatsappToken;

  public UpdateTenantSettingsRequest() {
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

  public String getWhatsappToken() {
    return whatsappToken;
  }

  public void setWhatsappToken(String whatsappToken) {
    this.whatsappToken = whatsappToken;
  }
}

