package com.julio.odentix.odentix_backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base reutilizable para todos los tests de integración (FASE0-06).
 *
 * <p>Levanta un PostgreSQL 16 real y desechable vía Testcontainers — nunca H2
 * ni mocks de base de datos. Los tests heredan de esta clase en vez de crear
 * su propio contenedor.
 *
 * <p>El contenedor es estático: se comparte entre todas las clases de test de
 * la JVM (más rápido que uno por clase). Solo requiere el daemon de Docker;
 * Testcontainers maneja el ciclo de vida solo.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> POSTGRES;

  static {
    POSTGRES = new PostgreSQLContainer<>("postgres:16")
        // Cada clase con contexto propio (falsos con @DynamicPropertySource,
        // @MockitoBean…) deja su pool Hikari abierto en la caché de Spring:
        // con el default de 100 conexiones PG se agota ("too many clients").
        .withCommand("postgres", "-c", "max_connections=200");
    POSTGRES.start();
  }

  // Inyecta la URL y credenciales del contenedor desechable, pisando los
  // valores de application-test.yml (que quedan como fallback local).
  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
