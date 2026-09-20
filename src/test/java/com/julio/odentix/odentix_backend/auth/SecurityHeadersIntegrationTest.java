package com.julio.odentix.odentix_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verificación explícita de la revisión CSRF/XSS de FASE12-01.
 *
 * <p>La API es REST pura con JWT Bearer stateless: sin sesiones, sin cookies y sin
 * HTML. Por eso CSRF no aplica (no hay sesión que un sitio malicioso pueda hacer
 * usar) y los errores nunca reflejan HTML. Este test lo deja verificado en vez de
 * asumido: respuestas de error en JSON, cabeceras anti-sniffing/framing presentes
 * y ausencia total de cookies de sesión.
 */
@AutoConfigureMockMvc
class SecurityHeadersIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Sin token: 401 en JSON con cabeceras de seguridad y sin cookies")
  void sinToken401JsonConCabecerasYSinCookies() throws Exception {
    mockMvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().exists("X-Frame-Options"))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }

  @Test
  @DisplayName("Login inválido: 401 en JSON (nunca HTML) y sin cookies de sesión")
  void loginInvalido401JsonSinCookies() throws Exception {
    String body = objectMapper.writeValueAsString(
        new LoginRequest("inexistente+seguridad@odontix.com", "cualquiera"));

    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", "198.51.100.99")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().doesNotExist("Set-Cookie"));
  }
}
