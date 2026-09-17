package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.dto.LogoutRequest;
import com.julio.odentix.odentix_backend.auth.dto.TokenRefreshRequest;
import com.julio.odentix.odentix_backend.auth.entity.RefreshToken;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.RefreshTokenRepository;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.RefreshTokenService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para renovación y revocación de sesiones (FASE1-IMPROVE).
 */
@AutoConfigureMockMvc
class AuthRefreshIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  private Tenant tenant;
  private User user;
  private String userEmail;

  @BeforeEach
  void setUp() {
    userEmail = "refresh_user+" + UUID.randomUUID() + "@odontix.com";
    tenant = tenantRepository.save(new Tenant("Clínica Refresh", "900777666-2"));
    user = userService.createUser(
        tenant.getId(),
        userEmail,
        "ClaveSegura123*",
        "Dr. Refresh",
        UserRole.odontologo
    );
  }

  private String[] loginAndGetTokens() throws Exception {
    LoginRequest request = new LoginRequest(userEmail, "ClaveSegura123*");
    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    return new String[]{json.get("accessToken").asText(), json.get("refreshToken").asText()};
  }

  @Test
  void rotacionExitosaEmiteNuevoAccessTokenYRefreshToken() throws Exception {
    String[] tokens = loginAndGetTokens();
    String originalRefreshToken = tokens[1];

    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(originalRefreshToken);

    MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresInSeconds").isNumber())
        .andReturn();

    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String newAccessToken = json.get("accessToken").asText();
    String newRefreshToken = json.get("refreshToken").asText();

    assertThat(newRefreshToken).isNotEqualTo(originalRefreshToken);

    // Verificar que el nuevo access token funciona en un endpoint protegido
    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + newAccessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(userEmail));
  }

  @Test
  void reusoDeTokenRevocadoDevuelve401() throws Exception {
    String[] tokens = loginAndGetTokens();
    String originalRefreshToken = tokens[1];

    // Primer refresco (rotación exitosa)
    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(originalRefreshToken);
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isOk());

    // Segundo intento con el token viejo (reúso indebido)
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Refresh token revocado"));
  }

  @Test
  void refreshTokenInexistenteDevuelve401() throws Exception {
    TokenRefreshRequest refreshRequest = new TokenRefreshRequest("tokenInexistente123456789");

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Refresh token inválido"));
  }

  @Test
  void refreshTokenExpiradoDevuelve401() throws Exception {
    String[] tokens = loginAndGetTokens();
    String refreshToken = tokens[1];

    // Modificar fecha de expiración en base de datos
    String hash = RefreshTokenService.hashToken(refreshToken);
    RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(hash).orElseThrow();
    tokenEntity.setExpiresAt(Instant.now().minusSeconds(3600));
    refreshTokenRepository.save(tokenEntity);

    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(refreshToken);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Refresh token expirado"));
  }

  @Test
  void refreshTokenConUsuarioInactivoDevuelve401() throws Exception {
    String[] tokens = loginAndGetTokens();
    String refreshToken = tokens[1];

    user.setActive(false);
    userRepository.save(user);

    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(refreshToken);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Usuario inactivo"));
  }

  @Test
  void logoutExitosoRevocaTokenEImpideNuevoRefresh() throws Exception {
    String[] tokens = loginAndGetTokens();
    String refreshToken = tokens[1];

    LogoutRequest logoutRequest = new LogoutRequest(refreshToken);

    mockMvc.perform(post("/api/v1/auth/logout")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(logoutRequest)))
        .andExpect(status().isNoContent());

    // Intentar refrescar tras logout
    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(refreshToken);
    mockMvc.perform(post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(refreshRequest)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Refresh token revocado"));
  }
}
