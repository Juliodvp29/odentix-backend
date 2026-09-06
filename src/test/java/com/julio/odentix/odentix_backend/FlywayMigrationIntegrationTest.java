package com.julio.odentix.odentix_backend;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Verifica que Flyway aplica las migraciones (V1, FASE0-05) sobre la base de
 * Testcontainers al levantar el contexto — el mecanismo que usará cada fase
 * siguiente para su esquema.
 *
 * <p>Nota: usa {@link DataSource} + JDBC plano a propósito. En esta revisión
 * el bean {@code JdbcTemplate} no se auto-configuró (pendiente de investigar
 * el reporte de condiciones con {@code --debug}); el DataSource sí existe
 * siempre porque Flyway y JPA lo exigen.
 */
class FlywayMigrationIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private DataSource dataSource;

  @Test
  void v1AplicadaYTablaProbeExiste() throws Exception {
    try (var connection = dataSource.getConnection();
        var stmt = connection.createStatement()) {
      try (var rs =
          stmt.executeQuery(
              "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true")) {
        rs.next();
        assertThat(rs.getInt(1)).isEqualTo(1);
      }
      try (var rs = stmt.executeQuery("SELECT COUNT(*) FROM migration_probe")) {
        rs.next();
        assertThat(rs.getInt(1)).isZero();
      }
    }
  }
}
