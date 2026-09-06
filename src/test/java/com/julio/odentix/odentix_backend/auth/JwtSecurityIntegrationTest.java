package com.julio.odentix.odentix_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la validación de JWT en Spring Security (FASE1-07).
 */
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private User user;
  private String validToken;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Clínica Especialistas", "900555444-3"));
    user = userService.createUser(
        tenant.getId(),
        "doctora.ana@especialistas.com",
        "ClaveSuperSegura1!",
        "Dra. Ana Rojas",
        UserRole.odontologo
    );
    validToken = jwtService.generateToken(user);
  }

  @Test
  void requestSinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"));
  }

  @Test
  void requestConTokenValidoDevuelve200YDatosDeUsuario() throws Exception {
    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + validToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(user.getId().toString()))
        .andExpect(jsonPath("$.email").value("doctora.ana@especialistas.com"))
        .andExpect(jsonPath("$.fullName").value("Dra. Ana Rojas"))
        .andExpect(jsonPath("$.role").value("odontologo"))
        .andExpect(jsonPath("$.tenantId").value(user.getTenant().getId().toString()));
  }

  @Test
  void requestConTokenManipuladoDevuelve401() throws Exception {
    String corruptedToken = validToken.substring(0, validToken.length() - 5) + "abcde";

    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + corruptedToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"));
  }

  @Test
  void requestConHeaderMalFormateadoDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Basic 123456"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"));
  }
}
