package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pruebas de integración para UserService (FASE1-05) con Testcontainers.
 *
 * <p>Verifica que la contraseña nunca se guarda en texto plano, leyendo el
 * campo directamente en la BD (criterio de aceptación del ticket).
 */
class UserServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UserService userService;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private DataSource dataSource;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    tenantA = tenantRepository.save(new Tenant("Clínica A", "900111111-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica B", "900222222-2"));
  }

  @Test
  void crearUsuarioGuardaHashBcryptYNoTextoPlano() throws Exception {
    String rawPassword = "Secreta123";

    User creado = userService.createUser(
        tenantA.getId(), "admin@clinicaa.com", rawPassword, "Admin A", UserRole.propietario);

    assertThat(creado.getId()).isNotNull();
    assertThat(creado.isActive()).isTrue();

    // Lectura directa en la BD, sin pasar por la entidad (criterio del ticket).
    String hashEnBd = leerPasswordHash(creado.getId().toString());
    assertThat(hashEnBd).isNotEqualTo(rawPassword);
    assertThat(hashEnBd).startsWith("$2a$");
    assertThat(passwordEncoder.matches(rawPassword, hashEnBd)).isTrue();
  }

  @Test
  void noPermiteEmailDuplicadoEnElMismoTenant() {
    userService.createUser(
        tenantA.getId(), "repite@clinicaa.com", "Clave123", "Uno", UserRole.recepcion);

    assertThatThrownBy(() -> userService.createUser(
        tenantA.getId(), "repite@clinicaa.com", "Otra123", "Dos", UserRole.recepcion))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void permiteMismoEmailEnDistintoTenant() {
    userService.createUser(
        tenantA.getId(), "mismo@email.com", "Clave123", "En A", UserRole.recepcion);

    User enB = userService.createUser(
        tenantB.getId(), "mismo@email.com", "Clave123", "En B", UserRole.recepcion);

    assertThat(enB.getId()).isNotNull();
  }

  private String leerPasswordHash(String userId) throws Exception {
    try (var connection = dataSource.getConnection();
        var stmt = connection.prepareStatement(
            "SELECT password_hash FROM users WHERE id = ?::uuid")) {
      stmt.setString(1, userId);
      try (var rs = stmt.executeQuery()) {
        rs.next();
        return rs.getString(1);
      }
    }
  }
}
