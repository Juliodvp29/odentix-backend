package com.julio.odentix.odentix_backend.shared.context;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pruebas de integración para verificar el ciclo de vida de TenantContext por petición (FASE1-08).
 * Valida que el tenant_id se pueble durante la petición y se limpie en el bloque finally.
 */
@AutoConfigureMockMvc
@Import(TenantContextIntegrationTest.TenantContextTestController.class)
class TenantContextIntegrationTest extends AbstractIntegrationTest {

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
    TenantContext.clear();

    Tenant tenant = tenantRepository.save(new Tenant("Clínica San Gabriel", "901234567-8"));
    user = userService.createUser(
        tenant.getId(),
        "dr.carlos@sangabriel.com",
        "ClaveSegura123!",
        "Dr. Carlos Mendoza",
        UserRole.propietario
    );
    validToken = jwtService.generateToken(user);
  }

  @Test
  void requestAutenticadaPueblaTenantContextDuranteLaPeticionYLoLimpiaDespues() throws Exception {
    assertThat(TenantContext.getTenantId()).isNull();

    mockMvc.perform(get("/api/v1/test-tenant-context")
            .header("Authorization", "Bearer " + validToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value(user.getTenant().getId().toString()));

    // Al finalizar la petición, el hilo debe quedar limpio (bloque finally)
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void requestSinTokenMantieneTenantContextLimpio() throws Exception {
    mockMvc.perform(get("/api/v1/test-tenant-context"))
        .andExpect(status().isUnauthorized());

    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void limpiezaOcurreInclusoSiOcurreErrorEnControlador() throws Exception {
    assertThat(TenantContext.getTenantId()).isNull();

    try {
      mockMvc.perform(get("/api/v1/test-tenant-context/error")
          .header("Authorization", "Bearer " + validToken));
    } catch (Exception ignored) {
      // El error es esperado
    }

    // El hilo debe quedar limpio sin importar la excepción en el controlador
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @RestController
  @RequestMapping("/api/v1/test-tenant-context")
  static class TenantContextTestController {

    @GetMapping
    public ResponseEntity<Map<String, String>> obtenerContexto() {
      UUID tenantId = TenantContext.getTenantId();
      return ResponseEntity.ok(Map.of(
          "tenantId", tenantId != null ? tenantId.toString() : "null"
      ));
    }

    @GetMapping("/error")
    public ResponseEntity<Void> simularError() {
      throw new RuntimeException("Error intencional para probar limpieza en finally");
    }
  }
}
