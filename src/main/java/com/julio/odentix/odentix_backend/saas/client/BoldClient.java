package com.julio.odentix.odentix_backend.saas.client;

import com.julio.odentix.odentix_backend.saas.client.BoldException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Cliente HTTP hacia Bold — API Link de pagos (FASE11-04).
 *
 * <p>Implementa solo lo documentado en `developers.bold.co` (verificado
 * 2026-09-19): base `https://integrations.api.bold.co`, header
 * `Authorization: x-api-key <llave>`, `POST /online/link/v1` (monto CLOSE/COP,
 * referencia única, descripción) y `GET /online/link/v1/{id}` con estados
 * `ACTIVE/PROCESSING/PAID/REJECTED/CANCELLED/EXPIRED`. Nada inventado.
 *
 * <p>Todo por variables de entorno (`BOLD_API_KEY` sin default —nunca
 * hardcodeada). Timeouts de 10s.
 */
@Component
public class BoldClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final RestClient restClient;
  private final String apiKey;

  public BoldClient(
      @Value("${odentix.saas.bold.api-base-url:https://integrations.api.bold.co}")
      String apiBaseUrl,
      @Value("${odentix.saas.bold.api-key:}") String apiKey) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder()
        .baseUrl(apiBaseUrl != null ? apiBaseUrl.strip() : "")
        .requestFactory(requestFactory)
        .build();
    this.apiKey = apiKey != null ? apiKey.strip() : "";
  }

  /**
   * Resultado de crear un link: identificador y URL de pago.
   */
  public record PaymentLink(String paymentLink, String url) {
  }

  /**
   * Crea un link de pago de monto cerrado en COP.
   *
   * @param totalAmountCop monto total en pesos (entero, sin decimales).
   * @param reference referencia única nuestra (max 60 chars, alfanumérica).
   * @param description descripción breve (2-100 chars).
   * @param payerEmail email al que Bold envía el link (opcional).
   * @param expirationEpochNanos expiración en nanosegundos Unix (opcional).
   * @return link creado.
   * @throws BoldException si falta la key o Bold rechaza/falla.
   */
  @SuppressWarnings("unchecked")
  public PaymentLink createPaymentLink(long totalAmountCop, String reference, String description,
      String payerEmail, Long expirationEpochNanos) {
    exigirApiKey();
    Map<String, Object> amount = new java.util.HashMap<>();
    amount.put("currency", "COP");
    amount.put("total_amount", totalAmountCop);
    amount.put("tip_amount", 0);
    Map<String, Object> body = new java.util.HashMap<>();
    body.put("amount_type", "CLOSE");
    body.put("amount", amount);
    body.put("reference", reference);
    body.put("description", description);
    if (payerEmail != null && !payerEmail.isBlank()) {
      body.put("payer_email", payerEmail.strip());
    }
    if (expirationEpochNanos != null) {
      body.put("expiration_date", expirationEpochNanos);
    }

    Map<String, Object> response = post("/online/link/v1", body);
    Map<String, Object> payload = payloadDe(response);
    Object link = payload.get("payment_link");
    Object url = payload.get("url");
    if (link == null || url == null) {
      throw new BoldException("Bold devolvió un link incompleto.");
    }
    return new PaymentLink(link.toString(), url.toString());
  }

  /**
   * Consulta el estado actual de un link (`ACTIVE`, `PAID`, etc.).
   */
  @SuppressWarnings("unchecked")
  public String getPaymentLinkStatus(String paymentLinkId) {
    exigirApiKey();
    Map<String, Object> response = get("/online/link/v1/" + paymentLinkId);
    Object status = response.get("status");
    if (status == null) {
      throw new BoldException("Bold devolvió un link sin estado.");
    }
    return status.toString();
  }

  // ---------------------------------------------------------------------------
  // HTTP interno
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private Map<String, Object> post(String path, Object body) {
    try {
      return restClient.post()
          .uri(path)
          .headers(headers -> {
            headers.set("Authorization", "x-api-key " + apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
          })
          .body(body)
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new BoldException(
          "Bold rechazó la solicitud (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (ResourceAccessException e) {
      throw new BoldException("No se pudo contactar a Bold (timeout o red).", e);
    } catch (RuntimeException e) {
      throw new BoldException("Fallo inesperado llamando a Bold.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> get(String path) {
    try {
      return restClient.get()
          .uri(path)
          .headers(headers -> headers.set("Authorization", "x-api-key " + apiKey))
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new BoldException(
          "Bold rechazó la consulta (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (ResourceAccessException e) {
      throw new BoldException("No se pudo contactar a Bold (timeout o red).", e);
    } catch (RuntimeException e) {
      throw new BoldException("Fallo inesperado consultando a Bold.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> payloadDe(Map<String, Object> response) {
    if (response == null || !(response.get("payload") instanceof Map)) {
      throw new BoldException("Respuesta de Bold con formato inesperado.");
    }
    return (Map<String, Object>) response.get("payload");
  }

  private void exigirApiKey() {
    if (apiKey.isEmpty()) {
      throw new BoldException(
          "Falta configurar BOLD_API_KEY: el cobro con Bold necesita la llave de identidad.");
    }
  }
}
