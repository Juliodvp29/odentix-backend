package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.sender.NotificationException;
import com.julio.odentix.odentix_backend.notification.sender.WhatsappNotificationSender;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del adaptador WhatsApp (FASE8-05) contra un servidor HTTP falso del
 * JDK — cero red real, cero credenciales (regla §10 de AGENTS.md).
 *
 * <p>Es un test unitario plano (sin Spring ni BD): el adaptador es un POJO
 * con el proveedor inyectado por constructor.
 */
class WhatsappSenderIntegrationTest {

  private HttpServer servidor;

  @AfterEach
  void detenerServidor() {
    if (servidor != null) {
      servidor.stop(0);
      servidor = null;
    }
  }

  // Los últimos dos null son TenantRepository y DataEncryptionService: sin
  // TenantContext no se tocan (contexto de sistema → global directo).
  private static WhatsappNotificationSender sender(String baseUrl, String numero, String token) {
    return new WhatsappNotificationSender(baseUrl, numero, token, null, null);
  }

  private int puertoLibre() throws IOException {
    try (var socket = new java.net.ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private String iniciarServidorFalso(int codigo, String respuesta,
      AtomicReference<String> cuerpoRecibido) throws IOException {
    servidor = HttpServer.create(new InetSocketAddress(0), 0);
    servidor.createContext("/", intercambio -> {
      if (cuerpoRecibido != null) {
        cuerpoRecibido.set(new String(
            intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      }
      byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
      intercambio.getResponseHeaders().set("Content-Type", "application/json");
      intercambio.sendResponseHeaders(codigo, bytes.length);
      intercambio.getResponseBody().write(bytes);
      intercambio.close();
    });
    servidor.start();
    return "http://localhost:" + servidor.getAddress().getPort();
  }

  @Test
  void envioExitosoLlamaAlEndpointConFormatoCloudApi() throws Exception {
    AtomicReference<String> cuerpo = new AtomicReference<>();
    String baseUrl = iniciarServidorFalso(200, "{\"messages\":[{\"id\":\"wamid.test\"}]}", cuerpo);

    WhatsappNotificationSender sender = sender(baseUrl, "999888", "token-falso");
    assertThat(sender.channel()).isEqualTo(NotificationChannel.whatsapp);

    sender.send("573001112233", "asunto ignorado", "Hola, tu cita quedó confirmada.");

    assertThat(cuerpo.get()).contains("\"to\":\"573001112233\"");
    assertThat(cuerpo.get()).contains("\"type\":\"text\"");
    assertThat(cuerpo.get()).contains("tu cita quedó confirmada");
    assertThat(cuerpo.get()).contains("\"messaging_product\":\"whatsapp\"");
  }

  @Test
  void rechazoDelProveedorLanzaConCodigoHttp() throws Exception {
    String baseUrl = iniciarServidorFalso(400,
        "{\"error\":{\"message\":\"número inválido\"}}", null);

    WhatsappNotificationSender sender = sender(baseUrl, "999888", "token-falso");

    assertThatThrownBy(() -> sender.send("no-un-numero", "", "Hola"))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("HTTP 400");
  }

  @Test
  void proveedorInalcanzableLanzaSinBloquear() throws Exception {
    int cerrado = puertoLibre();

    WhatsappNotificationSender sender =
        sender("http://localhost:" + cerrado, "999888", "token-falso");

    assertThatThrownBy(() -> sender.send("573001112233", "", "Hola"))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("contactar");
  }

  @Test
  void sinConfiguracionReportaErrorClaro() {
    WhatsappNotificationSender sender = sender("http://localhost:1", "", "");

    assertThatThrownBy(() -> sender.send("573001112233", "", "Hola"))
        .isInstanceOf(NotificationException.class)
        .hasMessageContaining("WHATSAPP_PHONE_NUMBER_ID");
  }
}
