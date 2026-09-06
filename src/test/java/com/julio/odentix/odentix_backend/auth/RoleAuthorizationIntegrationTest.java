package com.julio.odentix.odentix_backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.controller.RoleTestController;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la autorización basada en roles con @PreAuthorize (FASE1-11).
 *
 * <p>Verifica que los roles extraídos del JWT y mapeados a authorities (ROLE_*) restrinjan
 * adecuadamente el acceso a los métodos protegidos, retornando 403 Forbidden a usuarios con roles no autorizados.
 */
@AutoConfigureMockMvc
@Import(RoleTestController.class)
class RoleAuthorizationIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private String tokenPropietario;
  private String tokenRecepcion;
  private String tokenOdontologo;

  @BeforeEach
  void setUp() {
    Tenant tenant = tenantRepository.save(new Tenant("Clínica Odontológica Central", "900999888-1"));

    User userPropietario = userService.createUser(
        tenant.getId(),
        "propietario@central.com",
        "PassSegura123!",
        "Dr. Propietario",
        UserRole.propietario
    );
    tokenPropietario = jwtService.generateToken(userPropietario);

    User userRecepcion = userService.createUser(
        tenant.getId(),
        "recepcion@central.com",
        "PassSegura123!",
        "Recepción Odentix",
        UserRole.recepcion
    );
    tokenRecepcion = jwtService.generateToken(userRecepcion);

    User userOdontologo = userService.createUser(
        tenant.getId(),
        "odontologo@central.com",
        "PassSegura123!",
        "Dra. Especialista",
        UserRole.odontologo
    );
    tokenOdontologo = jwtService.generateToken(userOdontologo);
  }

  @Test
  @DisplayName("Usuario con rol PROPIETARIO accede con éxito (200 OK) a endpoint restringido a PROPIETARIO")
  void usuarioConRolPropietarioPuedeAccederAEndpointDePropietario() throws Exception {
    mockMvc.perform(get("/api/v1/test-roles/propietario")
            .header("Authorization", "Bearer " + tokenPropietario))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Acceso concedido a propietario"));
  }

  @Test
  @DisplayName("Criterio DoD: Usuario con rol RECEPCION recibe 403 Forbidden al llamar endpoint restringido a PROPIETARIO")
  void usuarioConRolRecepcionRecibe403EnEndpointDePropietario() throws Exception {
    mockMvc.perform(get("/api/v1/test-roles/propietario")
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"))
        .andExpect(jsonPath("$.message").value("No tienes permisos suficientes para acceder a este recurso"));
  }

  @Test
  @DisplayName("Usuario con rol ODONTOLOGO recibe 403 Forbidden al llamar endpoint restringido a PROPIETARIO")
  void usuarioConRolOdontologoRecibe403EnEndpointDePropietario() throws Exception {
    mockMvc.perform(get("/api/v1/test-roles/propietario")
            .header("Authorization", "Bearer " + tokenOdontologo))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Petición sin autenticación recibe 401 Unauthorized")
  void requestSinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/test-roles/propietario"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("No autorizado"));
  }

  @Test
  @DisplayName("Cualquier usuario autenticado (ej. RECEPCION) puede acceder a endpoint sin restricción de rol específica")
  void cualquierUsuarioAutenticadoPuedeAccederAEndpointGeneral() throws Exception {
    mockMvc.perform(get("/api/v1/test-roles/cualquiera")
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("Acceso concedido a cualquier rol autenticado"));
  }
}
