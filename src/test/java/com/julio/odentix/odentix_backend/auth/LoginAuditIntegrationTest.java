package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.audit.entity.AuditAction;
import com.julio.odentix.odentix_backend.audit.entity.AuditLog;
import com.julio.odentix.odentix_backend.audit.repository.AuditRepository;
import com.julio.odentix.odentix_backend.auth.dto.LoginRequest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la auditoría de login (FASE1-14).
 *
 * <p>Verifica que cada intento atribuible a un tenant deja una entrada
 * consultable en audit_log, sin registrar nunca la contraseña.
 */
@AutoConfigureMockMvc
class LoginAuditIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private AuditRepository auditRepository;

  private Tenant tenant;
  private User user;
  private String userEmail;

  @BeforeEach
  void setUp() {
    // Email único por método: la BD de Testcontainers se comparte entre métodos
    // y el login exige un único candidato por email (colisión → 401).
    userEmail = "higienista+" + UUID.randomUUID() + "@odontix.com";
    tenant = tenantRepository.save(new Tenant("Clínica Odontix Sur", "900333444-5"));
    user = userService.createUser(
        tenant.getId(),
        userEmail,
        "Limpieza456*",
        "Higienista Sur",
        UserRole.auxiliar);
  }

  @Test
  void loginExitosoRegistraLoginSuccess() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest(userEmail, "Limpieza456*"))))
        .andExpect(status().isOk());

    List<AuditLog> entradas = auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId());
    assertThat(entradas).hasSize(1);
    assertThat(entradas.get(0).getAction()).isEqualTo(AuditAction.login_success);
    assertThat(entradas.get(0).getUserId()).isEqualTo(user.getId());
    assertThat(entradas.get(0).getDetail()).containsEntry("email", userEmail);
    assertThat(entradas.get(0).getDetail()).doesNotContainKey("password");
  }

  @Test
  void passwordErroneaRegistraLoginFailedConUserId() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest(userEmail, "Erronea000"))))
        .andExpect(status().isUnauthorized());

    List<AuditLog> entradas = auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId());
    assertThat(entradas).hasSize(1);
    assertThat(entradas.get(0).getAction()).isEqualTo(AuditAction.login_failed);
    assertThat(entradas.get(0).getUserId()).isEqualTo(user.getId());
  }

  @Test
  void emailInexistenteSinTenantNoDejaRastro() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest("nadie@odontix.com", "Cualquiera123"))))
        .andExpect(status().isUnauthorized());

    // Sin tenant atribuible no se registra nada (decisión documentada FASE1-14).
    assertThat(auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId())).isEmpty();
  }

  @Test
  void emailInexistenteConTenantSeAtribuyeAlTenantDelRequest() throws Exception {
    mockMvc.perform(post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                new LoginRequest("nadie@odontix.com", "Cualquiera123", tenant.getId()))))
        .andExpect(status().isUnauthorized());

    List<AuditLog> entradas = auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenant.getId());
    assertThat(entradas).hasSize(1);
    assertThat(entradas.get(0).getAction()).isEqualTo(AuditAction.login_failed);
    assertThat(entradas.get(0).getUserId()).isNull();
  }
}
