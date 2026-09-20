package com.julio.odentix.odentix_backend.shared;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ErrorReporter;
import com.julio.odentix.odentix_backend.shared.exception.SentryErrorReporter;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Observabilidad de FASE12-03: Actuator ampliado y cableado de Sentry.
 *
 * <p>`/actuator/prometheus` y `/actuator/metrics` exponen solo métricas
 * JVM/HTTP (nunca datos de negocio) y requieren JWT — igual que cualquier
 * endpoint protegido. El reporte de 500 a Sentry queda verificado por
 * construcción: el contexto levanta `SentryErrorReporter` como `ErrorReporter`
 * (sin DSN es no-op en tests; con `SENTRY_DSN` en prod envía el evento) y
 * `GlobalExceptionHandlerTest` prueba que solo los 500 lo invocan.
 */
@AutoConfigureMockMvc
class ObservabilityIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private ErrorReporter errorReporter;

  private String token;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    Tenant tenant = tenantRepository.save(new Tenant("Clínica Observabilidad", "900777333-1"));
    User usuario = userService.createUser(
        tenant.getId(), "obs@odontix.com", "ClaveSegura123!", "Obs", UserRole.recepcion);
    token = jwtService.generateToken(usuario);
  }

  @Test
  @DisplayName("El ErrorReporter del contexto es el de Sentry")
  void errorReporterEsSentry() {
    assertInstanceOf(SentryErrorReporter.class, errorReporter);
  }

  @Test
  @DisplayName("Prometheus sin token devuelve 401")
  void prometheusSinToken401() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Prometheus y metrics con JWT devuelven métricas")
  void metricasConJwt200() throws Exception {
    mockMvc.perform(get("/actuator/prometheus")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(content().string(Matchers.containsString("jvm_")));

    mockMvc.perform(get("/actuator/metrics")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
