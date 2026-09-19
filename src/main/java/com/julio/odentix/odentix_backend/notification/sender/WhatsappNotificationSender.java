package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.crypto.DataEncryptionService;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adaptador de WhatsApp vía Cloud API (FASE8-05).
 *
 * <p>Se activa con {@code odentix.notifications.whatsapp.enabled=true}. El
 * número del negocio (`phoneNumberId`) y el token van por variables de entorno
 * sin defaults: sin ellos el adaptador reporta mala configuración en vez de
 * inventar credenciales (regla §10 de AGENTS.md). La aprobación del número y
 * las plantillas en Meta es un proceso administrativo fuera del código.
 *
 * <p>Timeouts cortos (5s): un proveedor caído falla rápido y el servicio lo
 * registra como intento `fallida` sin bloquear el flujo de negocio (mismo
 * principio de resiliencia del adaptador SMTP).
 *
 * <p><b>Número por clínica</b> (pre-Fase 12): las credenciales salen del tenant
 * (`whatsapp_phone_number_id` + token cifrado); sin ellas se usa el par global
 * de entorno como puente compartido, y sin ninguno se reporta mala
 * configuración en vez de inventar credenciales (regla §10 de AGENTS.md).
 */
@Component
@ConditionalOnProperty(
    prefix = "odentix.notifications.whatsapp",
    name = "enabled",
    havingValue = "true")
public class WhatsappNotificationSender implements NotificationSender {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final TenantRepository tenantRepository;
  private final DataEncryptionService encryptionService;
  private final String globalPhoneNumberId;
  private final String globalToken;

  public WhatsappNotificationSender(
      @Value("${odentix.notifications.whatsapp.api-base-url:https://graph.facebook.com/v21.0}")
      String apiBaseUrl,
      @Value("${odentix.notifications.whatsapp.phone-number-id:}") String phoneNumberId,
      @Value("${odentix.notifications.whatsapp.token:}") String token,
      TenantRepository tenantRepository,
      DataEncryptionService encryptionService) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder()
        .baseUrl(apiBaseUrl != null ? apiBaseUrl.strip() : "")
        .requestFactory(requestFactory)
        .build();
    this.globalPhoneNumberId = phoneNumberId != null ? phoneNumberId.strip() : "";
    this.globalToken = token != null ? token.strip() : "";
    this.tenantRepository = tenantRepository;
    this.encryptionService = encryptionService;
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.whatsapp;
  }

  @Override
  public void send(String recipient, String subject, String body) throws NotificationException {
    // WhatsApp no tiene asunto: se ignora y se envía solo el cuerpo.
    String[] credenciales = credenciales();
    String phoneNumberId = credenciales[0];
    String token = credenciales[1];
    if (phoneNumberId.isEmpty() || token.isEmpty()) {
      throw new NotificationException(
          "Falta configurar el número de WhatsApp de la clínica "
              + "(o WHATSAPP_PHONE_NUMBER_ID / WHATSAPP_TOKEN globales).");
    }
    if (recipient == null || recipient.isBlank()) {
      throw new NotificationException("El destinatario WhatsApp está vacío.");
    }
    Map<String, Object> payload = Map.of(
        "messaging_product", "whatsapp",
        "to", recipient.strip(),
        "type", "text",
        "text", Map.of("body", body != null ? body : ""));
    try {
      restClient.post()
          .uri("/{phoneNumberId}/messages", phoneNumberId)
          .headers(headers -> {
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);
          })
          .body(payload)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw new NotificationException(
          "WhatsApp rechazó el envío (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (ResourceAccessException e) {
      throw new NotificationException(
          "No se pudo contactar a WhatsApp (timeout o red).", e);
    } catch (RuntimeException e) {
      throw new NotificationException("Fallo inesperado enviando por WhatsApp.", e);
    }
  }

  /**
   * Resuelve número+token: primero los de la clínica (token descifrado), si no
   * el par global como puente compartido. Sin contexto de tenant va directo al
   * global. Un descifrado fallido (llave rotada) se reporta, nunca se ignora.
   *
   * @return array {phoneNumberId, token} (alguno puede ir vacío).
   */
  private String[] credenciales() {
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId != null) {
      var clinica = tenantRepository.findById(tenantId).orElse(null);
      String numero = clinica != null && clinica.getWhatsappPhoneNumberId() != null
          ? clinica.getWhatsappPhoneNumberId().strip() : "";
      String cifrado = clinica != null && clinica.getWhatsappTokenCifrado() != null
          ? clinica.getWhatsappTokenCifrado() : "";
      if (!numero.isEmpty() && !cifrado.isEmpty()) {
        try {
          String token = encryptionService.descifrar(cifrado);
          if (!token.isBlank()) {
            return new String[]{numero, token.strip()};
          }
        } catch (RuntimeException e) {
          throw new NotificationException(
              "No se pudo descifrar el token de WhatsApp de la clínica.", e);
        }
      }
      if (!numero.isEmpty() || !cifrado.isEmpty()) {
        // Credencial a medias: ni la propia ni la global (evita que un número
        // huérfano salga por el puente compartido o viceversa).
        return new String[]{"", ""};
      }
    }
    return new String[]{globalPhoneNumberId, globalToken};
  }
}
