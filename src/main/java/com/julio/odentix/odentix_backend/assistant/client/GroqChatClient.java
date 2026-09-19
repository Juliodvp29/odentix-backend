package com.julio.odentix.odentix_backend.assistant.client;

import com.julio.odentix.odentix_backend.assistant.AssistantException;
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
 * Cliente HTTP hacia el proveedor de LLM (FASE10-01: Groq, API compatible con
 * OpenAI — verificado en sus docs: base `https://api.groq.com/openai/v1`,
 * `POST /chat/completions`, Bearer `GROQ_API_KEY`).
 *
 * <p>Toda la configuración sale de variables de entorno (`GROQ_API_KEY` sin
 * default —nunca hardcodeada—, `GROQ_MODEL` y `GROQ_API_BASE_URL` con defaults
 * documentados y sobreescribibles, porque los modelos rotan). Timeouts de 15s:
 * el timeout corto + fallback a plantilla fija se endurece en FASE10-03.
 */
@Component
public class GroqChatClient {

  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final RestClient restClient;
  private final String apiKey;
  private final String model;

  public GroqChatClient(
      @Value("${odentix.assistant.groq.api-base-url:https://api.groq.com/openai/v1}")
      String apiBaseUrl,
      @Value("${odentix.assistant.groq.api-key:}") String apiKey,
      @Value("${odentix.assistant.groq.model:openai/gpt-oss-20b}") String model) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(TIMEOUT);
    requestFactory.setReadTimeout(TIMEOUT);
    this.restClient = RestClient.builder()
        .baseUrl(apiBaseUrl != null ? apiBaseUrl.strip() : "")
        .requestFactory(requestFactory)
        .build();
    this.apiKey = apiKey != null ? apiKey.strip() : "";
    this.model = model != null && !model.isBlank() ? model.strip() : "openai/gpt-oss-20b";
  }

  /**
   * Envía los mensajes y devuelve el contenido de la primera opción.
   *
   * @param messages mensajes OpenAI (`role`/`content`).
   * @param temperature creatividad (baja para respuestas factuales).
   * @param maxTokens tope de tokens generados.
   * @return texto de la respuesta.
   * @throws AssistantException si falta la key o el proveedor falla.
   */
  @SuppressWarnings("unchecked")
  public String chat(List<Map<String, String>> messages, double temperature, int maxTokens) {
    if (apiKey.isEmpty()) {
      throw new AssistantException(
          "Falta configurar GROQ_API_KEY: el asistente necesita clave del proveedor.");
    }
    Map<String, Object> request = Map.of(
        "model", model,
        "messages", messages,
        "temperature", temperature,
        "max_completion_tokens", maxTokens);
    Map<String, Object> response;
    try {
      response = restClient.post()
          .uri("/chat/completions")
          .headers(headers -> {
            headers.setBearerAuth(apiKey);
            headers.setContentType(MediaType.APPLICATION_JSON);
          })
          .body(request)
          .retrieve()
          .body(Map.class);
    } catch (RestClientResponseException e) {
      throw new AssistantException(
          "El proveedor de IA rechazó la solicitud (HTTP " + e.getStatusCode().value() + ").", e);
    } catch (ResourceAccessException e) {
      throw new AssistantException("No se pudo contactar al proveedor de IA (timeout o red).", e);
    } catch (RuntimeException e) {
      throw new AssistantException("Fallo inesperado del proveedor de IA.", e);
    }

    try {
      List<Object> choices = (List<Object>) response.get("choices");
      Map<String, Object> first = (Map<String, Object>) choices.get(0);
      Map<String, Object> message = (Map<String, Object>) first.get("message");
      String content = (String) message.get("content");
      if (content == null || content.isBlank()) {
        throw new AssistantException("El proveedor de IA devolvió una respuesta vacía.");
      }
      return content.strip();
    } catch (AssistantException e) {
      throw e;
    } catch (RuntimeException | java.lang.Error e) {
      throw new AssistantException("Respuesta del proveedor de IA con formato inesperado.", e);
    }
  }

  /**
   * Modelo en uso (se devuelve en la respuesta para trazabilidad).
   */
  public String getModel() {
    return model;
  }
}
