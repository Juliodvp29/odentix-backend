package com.julio.odentix.odentix_backend.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la validación de JWT en Spring Security (FASE1-07)
 * y verificación en tiempo real del usuario en base de datos (FASE1-IMPROVE).
 */
@AutoConfigureMockMvc
class JwtSecurityIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserRepository userRepository;

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
        .andExpect(header().string("WWW-Authenticate", "Bearer error=\"unauthorized\""))
        .andExpect(jsonPath("$.error").value("No autorizado"))
        .andExpect(jsonPath("$.code").value("token_missing"));
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
        .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
        .andExpect(header().string("WWW-Authenticate", containsString("The access token is invalid or tampered")))
        .andExpect(jsonPath("$.error").value("No autorizado"))
        .andExpect(jsonPath("$.code").value("token_invalid"));
  }

  @Test
  void requestConTokenExpiradoDevuelve401() throws Exception {
    Date pastIssuedAt = Date.from(Instant.now().minus(2, ChronoUnit.HOURS));
    Date pastExpiration = Date.from(Instant.now().minus(1, ChronoUnit.HOURS));
    String expiredToken = jwtService.generateToken(user, pastIssuedAt, pastExpiration);

    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + expiredToken))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", containsString("error=\"invalid_token\"")))
        .andExpect(header().string("WWW-Authenticate", containsString("The access token expired")))
        .andExpect(jsonPath("$.error").value("No autorizado"))
        .andExpect(jsonPath("$.code").value("token_expired"))
        .andExpect(jsonPath("$.message").value("El token de acceso ha expirado"));
  }

  @Test
  void requestConHeaderMalFormateadoDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Basic 123456"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"));
  }

  @Test
  void usuarioDesactivadoEnBdRechazaRequestAunqueTokenSeaValido() throws Exception {
    user.setActive(false);
    userRepository.save(user);

    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + validToken))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"))
        .andExpect(jsonPath("$.code").value("user_inactive"));
  }

  @Test
  void cambioDeRolEnBdSeReflejaEnTiempoReal() throws Exception {
    user.setRole(UserRole.propietario);
    userRepository.save(user);

    mockMvc.perform(get("/api/v1/me")
            .header("Authorization", "Bearer " + validToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("propietario"));
  }
}
