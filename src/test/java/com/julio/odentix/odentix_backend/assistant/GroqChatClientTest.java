package com.julio.odentix.odentix_backend.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.assistant.client.GroqChatClient;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas planas del cliente Groq (FASE10-01): sin Spring, contra un servidor
 * falso local. Cubren parseo, rechazo del proveedor y falta de API key (502
 * en el endpoint).
 */
class GroqChatClientTest {

  private HttpServer servidor;

  @AfterEach
  void detener() {
    if (servidor != null) {
      servidor.stop(0);
      servidor = null;
    }
  }

  private String falso(int codigo, String json) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext("/chat/completions", intercambio -> {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(codigo, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    servidor.start();
    return "http://localhost:" + servidor.getAddress().getPort();
  }

  private static List<Map<String, String>> mensajes() {
    return List.of(Map.of("role", "user", "content", "hola"));
  }

  @Test
  void sinApiKeyFallaClaro() {
    GroqChatClient client =
        new GroqChatClient("http://localhost:1", "", "m");

    assertThatThrownBy(() -> client.chat(mensajes(), 0.2, 50))
        .isInstanceOf(AssistantException.class)
        .hasMessageContaining("GROQ_API_KEY");
  }

  @Test
  void rechazoDelProveedorLanzaConCodigo() throws Exception {
    String base = falso(401, "{\"error\":{\"message\":\"key inválida\"}}");
    GroqChatClient client = new GroqChatClient(base, "k", "m");

    assertThatThrownBy(() -> client.chat(mensajes(), 0.2, 50))
        .isInstanceOf(AssistantException.class)
        .hasMessageContaining("HTTP 401");
  }

  @Test
  void respuestaMalformadaLanza() throws Exception {
    String base = falso(200, "{\"object\":\"chat.completion\"}");
    GroqChatClient client = new GroqChatClient(base, "k", "m");

    assertThatThrownBy(() -> client.chat(mensajes(), 0.2, 50))
        .isInstanceOf(AssistantException.class);
  }

  @Test
  void respuestaOkDevuelveContenido() throws Exception {
    String base = falso(200, "{\"choices\":[{\"message\":{\"content\":\"  Hola clínica.  \"}}]}");
    GroqChatClient client = new GroqChatClient(base, "k", "mi-modelo");

    assertThat(client.chat(mensajes(), 0.2, 50)).isEqualTo("Hola clínica.");
    assertThat(client.getModel()).isEqualTo("mi-modelo");
  }
}
