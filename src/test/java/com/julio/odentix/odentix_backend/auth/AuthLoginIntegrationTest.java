package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para el endpoint de login y emisión de JWT (FASE1-06).
 */
@AutoConfigureMockMvc
class AuthLoginIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private Tenant tenant;
  private String userEmail;

  @BeforeEach
  void setUp() {
    // Email único por método: la BD de Testcontainers se comparte entre métodos
    // y el login exige un único candidato por email (colisión → 401 aunque la
    // clave sea correcta). Antes este test dependía del orden de ejecución.
    userEmail = "doctor+" + UUID.randomUUID() + "@odontix.com";
    tenant = tenantRepository.save(new Tenant("Clínica Odontix Norte", "900999888-1"));
    userService.createUser(
        tenant.getId(),
        userEmail,
        "ClaveSegura123*",
        "Dr. Mario Casas",
        UserRole.odontologo
    );
  }

  @Test
  void loginExitosoDevuelveTokenYDatosDeUsuario() throws Exception {
    LoginRequest request = new LoginRequest(userEmail, "ClaveSegura123*");

    MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.expiresInSeconds").isNumber())
        .andExpect(jsonPath("$.user.email").value(userEmail))
        .andExpect(jsonPath("$.user.role").value("odontologo"))
        .andExpect(jsonPath("$.user.tenantId").value(tenant.getId().toString()))
        .andReturn();

    String responseBody = result.getResponse().getContentAsString();
    String token = objectMapper.readTree(responseBody).get("accessToken").asText();

    assertThat(jwtService.validateToken(token)).isTrue();
    assertThat(jwtService.extractRole(token)).isEqualTo("odontologo");
    assertThat(jwtService.extractTenantId(token)).isEqualTo(tenant.getId());
    assertThat(jwtService.extractEmail(token)).isEqualTo(userEmail);
  }

  @Test
  void loginConPasswordErroneaDevuelve401() throws Exception {
    LoginRequest request = new LoginRequest(userEmail, "PasswordErronea999");

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
  }

  @Test
  void loginConUsuarioInexistenteDevuelve401() throws Exception {
    LoginRequest request = new LoginRequest("fantasma@odontix.com", "CualquierPassword");

    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
  }
}
