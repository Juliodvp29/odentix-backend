package com.julio.odentix.odentix_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.LoginRateLimitService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para el rate limiting por IP y bloqueo temporal por cuenta en /api/v1/auth/login.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "security.rate-limit.login.max-requests-per-minute=10",
    "security.rate-limit.login.max-failed-attempts=5",
    "security.rate-limit.login.lockout-duration-minutes=15"
})
class LoginRateLimitIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private LoginRateLimitService loginRateLimitService;

  private String userEmail;
  private final String passwordCorrecta = "ClaveSegura123*";

  @BeforeEach
  void setUp() {
    loginRateLimitService.reset();

    userEmail = "ratelimit_user+" + UUID.randomUUID() + "@odontix.com";
    Tenant tenant = tenantRepository.save(new Tenant("Clínica Rate Limit", "900888111-1"));
    userService.createUser(
        tenant.getId(),
        userEmail,
        passwordCorrecta,
        "Dr. Rate Limit",
        UserRole.odontologo
    );
  }

  @Test
  @DisplayName("Superar límite de peticiones por minuto por IP devuelve 429 con cabecera Retry-After")
  void excesoDePeticionesPorIpDevuelve429() throws Exception {
    String testIp = "192.168.1.100";

    // Enviar 10 solicitudes permitidas dentro del minuto (usando emails distintos para no disparar el bloqueo de cuenta)
    for (int i = 0; i < 10; i++) {
      LoginRequest request = new LoginRequest("test_ip_" + i + "@odontix.com", "cualquiera");
      mockMvc.perform(post("/api/v1/auth/login")
              .header("X-Forwarded-For", testIp)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
    }

    // La solicitud número 11 debe ser bloqueada inmediatamente con 429 por la IP
    LoginRequest req11 = new LoginRequest("test_ip_11@odontix.com", "cualquiera");
    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", testIp)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req11)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error").value("Límite de peticiones excedido"))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
  }

  @Test
  @DisplayName("5 fallos consecutivos bloquean temporalmente el email con 429")
  void cincoFallosConsecutivosPorEmailBloqueanLaCuentaCon429() throws Exception {
    LoginRequest badRequest = new LoginRequest(userEmail, "PasswordErronea");
    String badJson = objectMapper.writeValueAsString(badRequest);

    // Enviar 5 intentos fallidos desde IPs distintas para no activar el rate limit de IP
    for (int i = 1; i <= 5; i++) {
      mockMvc.perform(post("/api/v1/auth/login")
              .header("X-Forwarded-For", "10.0.1." + i)
              .contentType(MediaType.APPLICATION_JSON)
              .content(badJson))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }

    // El 6to intento (incluso con contraseña correcta) es bloqueado por cuenta con 429
    LoginRequest goodRequest = new LoginRequest(userEmail, passwordCorrecta);
    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", "10.0.1.99")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(goodRequest)))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.error").value("Cuenta temporalmente bloqueada"))
        .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
  }

  @Test
  @DisplayName("Un login exitoso resetea el contador de fallos para ese email")
  void loginExitosoReseteaContadorDeFallos() throws Exception {
    LoginRequest badRequest = new LoginRequest(userEmail, "PasswordErronea");
    String badJson = objectMapper.writeValueAsString(badRequest);

    // 3 intentos fallidos (menos de los 5 del bloqueo)
    for (int i = 1; i <= 3; i++) {
      mockMvc.perform(post("/api/v1/auth/login")
              .header("X-Forwarded-For", "10.0.2." + i)
              .contentType(MediaType.APPLICATION_JSON)
              .content(badJson))
          .andExpect(status().isUnauthorized());
    }

    // Login exitoso
    LoginRequest goodRequest = new LoginRequest(userEmail, passwordCorrecta);
    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", "10.0.2.10")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(goodRequest)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty());

    // 3 intentos fallidos más (no debe bloquear porque el contador se reseteó)
    for (int i = 1; i <= 3; i++) {
      mockMvc.perform(post("/api/v1/auth/login")
              .header("X-Forwarded-For", "10.0.2." + (20 + i))
              .contentType(MediaType.APPLICATION_JSON)
              .content(badJson))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
    }
  }

  @Test
  @DisplayName("Diferentes IPs tienen límites de peticiones independientes")
  void diferentesIpsTienenLimitesIndependientes() throws Exception {
    String ipBloqueada = "192.168.5.10";
    String ipLimpia = "192.168.5.20";

    // Agotar límite para la primera IP (usando emails distintos)
    for (int i = 0; i < 10; i++) {
      LoginRequest request = new LoginRequest("ip_indep_" + i + "@odontix.com", "cualquiera");
      mockMvc.perform(post("/api/v1/auth/login")
              .header("X-Forwarded-For", ipBloqueada)
              .contentType(MediaType.APPLICATION_JSON)
              .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnauthorized());
    }

    // La primera IP recibe 429
    LoginRequest reqBlocked = new LoginRequest("ip_indep_blocked@odontix.com", "cualquiera");
    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", ipBloqueada)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reqBlocked)))
        .andExpect(status().isTooManyRequests());

    // La segunda IP puede realizar peticiones normalmente (recibe 401 por credenciales, no 429)
    LoginRequest reqClean = new LoginRequest("ip_indep_clean@odontix.com", "cualquiera");
    mockMvc.perform(post("/api/v1/auth/login")
            .header("X-Forwarded-For", ipLimpia)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(reqClean)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Credenciales inválidas"));
  }
}
