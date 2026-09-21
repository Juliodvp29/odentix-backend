package com.julio.odentix.odentix_backend.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Preflight CORS desde el origen del frontend de desarrollo.
 *
 * <p>El navegador envía OPTIONS con Origin y Access-Control-Request-Method
 * antes del POST real: sin Access-Control-Allow-Origin en la respuesta,
 * bloquea la llamada aunque el endpoint funcione con curl.
 */
@AutoConfigureMockMvc
class CorsPreflightIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("Preflight al login responde con el origen de desarrollo permitido")
  void preflightLoginPermiteOrigenDesarrollo() throws Exception {
    mockMvc.perform(options("/api/v1/auth/login")
            .header("Origin", "http://localhost:4200")
            .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
  }

  @Test
  @DisplayName("Preflight a endpoint protegido pasa sin JWT y con origen permitido")
  void preflightProtegidoPasaSinJwt() throws Exception {
    mockMvc.perform(options("/api/v1/me")
            .header("Origin", "http://localhost:4200")
            .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
  }
}
