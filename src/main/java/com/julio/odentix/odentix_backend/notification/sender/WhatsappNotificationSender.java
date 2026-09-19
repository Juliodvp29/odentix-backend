package com.julio.odentix.odentix_backend.notification.sender;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import java.time.Duration;
import java.util.Map;
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
 */
@Component
@ConditionalOnProperty(
    prefix = "odentix.notifications.whatsapp",
    name = "enabled",
    havingValue = "true")
public class WhatsappNotificationSender implements NotificationSender {

  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final RestClient restClient;
  private final String phoneNumberId;
  private final String token;

  public WhatsappNotificationSender(
      @Value("${odentix.notifications.whatsapp.api-base-url:https://graph.facebook.com/v21.0}")
      String apiBaseUrl,
      @Value("${odentix.notifications.whatsapp.phone-number-id:}") String phoneNumberId,
      @Value("${odentix.notifications.whatsapp.token:}") String token) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder()
        .baseUrl(apiBaseUrl != null ? apiBaseUrl.strip() : "")
        .requestFactory(requestFactory)
        .build();
    this.phoneNumberId = phoneNumberId != null ? phoneNumberId.strip() : "";
    this.token = token != null ? token.strip() : "";
  }

  @Override
  public NotificationChannel channel() {
    return NotificationChannel.whatsapp;
  }

  @Override
  public void send(String recipient, String subject, String body) throws NotificationException {
    // WhatsApp no tiene asunto: se ignora y se envía solo el cuerpo.
    if (phoneNumberId.isEmpty() || token.isEmpty()) {
      throw new NotificationException(
          "Falta configurar WHATSAPP_PHONE_NUMBER_ID o WHATSAPP_TOKEN.");
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
}
