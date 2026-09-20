package com.julio.odentix.odentix_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshRequest;
import com.julio.odentix.odentix_backend.auth.service.PublicEndpointRateLimitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Rate limiting de los endpoints públicos sin JWT distintos del login (FASE12-01).
 *
 * <p>El login ya tiene su control propio ({@code LoginRateLimitIntegrationTest}).
 * Aquí se cubre {@code POST /api/v1/auth/refresh} (fuerza bruta sobre refresh
 * tokens opacos) y {@code POST /api/v1/billing/webhooks/bold} (spam/DoS anónimo):
 * superado el límite, ambos devuelven 429 con cabecera Retry-After.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "security.rate-limit.public.refresh.max-requests-per-minute=3",
    "security.rate-limit.public.webhook.max-requests-per-minute=3"
})
class PublicEndpointRateLimitIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private PublicEndpointRateLimitService publicEndpointRateLimitService;

  @BeforeEach
  void setUp() {
    publicEndpointRateLimitService.reset();
  }

  @Test
  @DisplayName("Superar el límite en /auth/refresh devuelve 429 con Retry-After")
  void excesoEnRefreshDevuelve429() throws Exception {
    String ip = "198.51.100.10";
    String body = objectMapper.writeValueAsString(
        new TokenRefreshRequest("refresh-token-inexistente"));

    // 3 intentos permitidos: token inválido → 401 (pasa el rate limit, falla el negocio)
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(post("/api/v1/auth/refresh")
              .header("X-Forwarded-For", ip)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isUnauthorized());
    }

    // 4to intento: bloqueado por IP con 429 antes de evaluar el token
    mockMvc.perform(post("/api/v1/auth/refresh")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error").value("Límite de peticiones excedido"))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
  }

  @Test
  @DisplayName("Superar el límite en el webhook de Bold devuelve 429 con Retry-After")
  void excesoEnWebhookDevuelve429() throws Exception {
    String ip = "203.0.113.20";

    // 3 intentos permitidos: sin firma válida → 400 (pasa el rate limit, falla la firma)
    for (int i = 0; i < 3; i++) {
      mockMvc.perform(post("/api/v1/billing/webhooks/bold")
              .header("X-Forwarded-For", ip)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isBadRequest());
    }

    // 4to intento: bloqueado por IP con 429 antes de evaluar la firma
    mockMvc.perform(post("/api/v1/billing/webhooks/bold")
            .header("X-Forwarded-For", ip)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"));
  }

  @Test
  @DisplayName("El límite del webhook es independiente por IP")
  void limiteWebhookIndependientePorIp() throws Exception {
    String ipBloqueada = "203.0.113.30";
    String ipLimpia = "203.0.113.31";

    for (int i = 0; i < 3; i++) {
      mockMvc.perform(post("/api/v1/billing/webhooks/bold")
              .header("X-Forwarded-For", ipBloqueada)
              .contentType(MediaType.APPLICATION_JSON)
              .content("{}"))
          .andExpect(status().isBadRequest());
    }
    mockMvc.perform(post("/api/v1/billing/webhooks/bold")
            .header("X-Forwarded-For", ipBloqueada)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isTooManyRequests());

    // Otra IP sigue pasando el rate limit (400 por firma, no 429)
    mockMvc.perform(post("/api/v1/billing/webhooks/bold")
            .header("X-Forwarded-For", ipLimpia)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
