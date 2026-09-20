package com.julio.odentix.odentix_backend.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.JwtValidationResult;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * MDC `tenant_id` para logs estructurados (FASE12-04).
 *
 * <p>El filtro pone el tenant en el MDC junto al `TenantContext` y lo limpia en
 * el `finally`: ningún hilo del pool conserva el tenant de otra request en sus
 * logs. Sin JWT válido no hay MDC (nada que filtrar).
 */
class JwtAuthenticationMdcTest {

  private JwtService jwtService;
  private UserRepository userRepository;
  private JwtAuthenticationFilter filter;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    MDC.clear();
    // El filtro bajo prueba escribe en el SecurityContextHolder del hilo (igual
    // que en producción); limpiarlo evita fugas al siguiente test de la JVM.
    SecurityContextHolder.clearContext();
    jwtService = mock(JwtService.class);
    userRepository = mock(UserRepository.class);
    filter = new JwtAuthenticationFilter(jwtService, userRepository);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    MDC.clear();
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Con JWT válido el MDC lleva el tenant durante la request y se limpia después")
  void mdcConTenantYLimpiezaPosterior() throws Exception {
    User usuario = mock(User.class);
    when(usuario.isActive()).thenReturn(true);
    when(usuario.getRole()).thenReturn(UserRole.odontologo);
    when(usuario.getEmail()).thenReturn("mts@odontix.com");
    when(jwtService.validateTokenResult("buen-token")).thenReturn(JwtValidationResult.VALID);
    when(jwtService.extractUserId("buen-token")).thenReturn(userId);
    when(jwtService.extractTenantId("buen-token")).thenReturn(tenantId);
    when(userRepository.findByIdAndTenantId(userId, tenantId))
        .thenReturn(Optional.of(usuario));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    request.addHeader("Authorization", "Bearer buen-token");
    AtomicReference<String> mdcDentro = new AtomicReference<>();
    FilterChain chain = (req, res) -> mdcDentro.set(MDC.get("tenant_id"));

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertEquals(tenantId.toString(), mdcDentro.get());
    assertNull(MDC.get("tenant_id"));
    assertNull(TenantContext.getTenantId());
  }

  @Test
  @DisplayName("Con token inválido no hay MDC de tenant")
  void tokenInvalidoSinMdc() throws Exception {
    when(jwtService.validateTokenResult(any())).thenReturn(JwtValidationResult.INVALID);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
    request.addHeader("Authorization", "Bearer mal-token");
    AtomicReference<String> mdcDentro = new AtomicReference<>("nunca-ejecutado");
    FilterChain chain = (req, res) -> mdcDentro.set(MDC.get("tenant_id"));

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertNull(mdcDentro.get());
    assertNull(MDC.get("tenant_id"));
  }
}
