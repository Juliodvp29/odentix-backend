package com.julio.odentix.odentix_backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para UserRepository (FASE1-02 y FASE1-03) con Testcontainers.
 *
 * <p>Verifica persistencia de usuarios con roles, constraint único de email por tenant,
 * y soporte multi-tenant (mismo email en distintas clínicas).
 */
class UserRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    tenantA = tenantRepository.save(new Tenant("Clínica A", "900111111-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica B", "900222222-2"));
  }

  @Test
  void persistirYRecuperarUsuarioConRol() {
    User user = new User(
        tenantA,
        "doctor.perez@clinicaa.com",
        "$2a$10$dummyHashDePruebaParaPassword",
        "Dr. Juan Pérez",
        UserRole.odontologo
    );

    User guardado = userRepository.save(user);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getEmail()).isEqualTo("doctor.perez@clinicaa.com");
    assertThat(guardado.getRole()).isEqualTo(UserRole.odontologo);
    assertThat(guardado.isActive()).isTrue();
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    Optional<User> recuperado = userRepository.findByTenantIdAndEmail(tenantA.getId(), "doctor.perez@clinicaa.com");
    assertThat(recuperado).isPresent();
    assertThat(recuperado.get().getFullName()).isEqualTo("Dr. Juan Pérez");
    assertThat(recuperado.get().getRole()).isEqualTo(UserRole.odontologo);
  }

  @Test
  void noPermitirEmailsDuplicadosEnElMismoTenant() {
    User user1 = new User(
        tenantA,
        "contacto@clinicaa.com",
        "hash1",
        "Usuario Uno",
        UserRole.recepcion
    );
    userRepository.saveAndFlush(user1);

    // Mismo email (incluso probando case-insensitivity con citext) en el mismo tenant
    User user2 = new User(
        tenantA,
        "CONTACTO@clinicaa.com",
        "hash2",
        "Usuario Dos",
        UserRole.auxiliar
    );

    assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void permitirMismoEmailEnTenantsDistintos() {
    // El mismo email en Tenant A y Tenant B debe permitirse (regla multi-tenant de FASE1-02)
    User userTenantA = new User(
        tenantA,
        "administrador@global.com",
        "hashA",
        "Admin Clínica A",
        UserRole.propietario
    );
    User userTenantB = new User(
        tenantB,
        "administrador@global.com",
        "hashB",
        "Admin Clínica B",
        UserRole.propietario
    );

    User guardadoA = userRepository.saveAndFlush(userTenantA);
    User guardadoB = userRepository.saveAndFlush(userTenantB);

    assertThat(guardadoA.getId()).isNotNull();
    assertThat(guardadoB.getId()).isNotNull();
    assertThat(guardadoA.getId()).isNotEqualTo(guardadoB.getId());
  }
}
