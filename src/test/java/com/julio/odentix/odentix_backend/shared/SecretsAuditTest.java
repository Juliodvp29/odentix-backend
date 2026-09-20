package com.julio.odentix.odentix_backend.shared;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/**
 * Auditoría automatizada de FASE12-02: ningún secreto hardcodeado en el repo.
 *
 * <p>Regla: toda propiedad de configuración cuyo nombre indique un secreto
 * (password, secret, api-key, token…) debe resolverse desde variables de
 * entorno (`${VAR}` o `${VAR:}`), nunca con un valor literal real. La única
 * excepción es el JWT de desarrollo, documentado como no-operativo en
 * `application.yml` (prod lo sobreescribe sin default vía `JWT_SECRET`).
 *
 * <p>Además verifica que ninguna migración Flyway cree roles con `BYPASSRLS`
 * o `SUPERUSER`: los roles los gestiona el proveedor (Render), y el rol de la
 * app nunca debe saltarse el RLS (ver sección 16 de `docs/schema.sql`).
 * La comprobación del rol real de producción es operativa (dashboard de
 * Render: `SELECT rolname, rolbypassrls FROM pg_roles;`) y no puede hacerse
 * desde código — este test cubre que el repo no lo contradiga.
 */
class SecretsAuditTest {

  private static final Set<String> SECRET_KEY_NAMES = Set.of(
      "password", "secret", "api-key", "token", "webhook-secret",
      "secret-key", "access-key", "private-key", "client-secret");

  // default de desarrollo documentado como no-operativo (application.yml: solo dev).
  private static final String DEV_JWT_PREFIX = "odentix_super_secret_jwt_key_for_development";

  @Test
  @DisplayName("Ningún secreto literal en application.yml ni application-prod.yml")
  void sinSecretosLiteralesEnConfiguracion() throws Exception {
    List<String> hallazgos = new ArrayList<>();
    for (String archivo : List.of("application.yml", "application-prod.yml")) {
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(archivo)) {
        assertTrue(in != null, "No se encontró en el classpath: " + archivo);
        Object raiz = new Yaml().load(in);
        revisarNodo("", raiz, hallazgos);
      }
    }
    assertTrue(hallazgos.isEmpty(),
        "Secretos literales hardcodeados (deben ser ${VAR}): " + hallazgos);
  }

  @Test
  @DisplayName("Ninguna migración crea roles con BYPASSRLS ni SUPERUSER")
  void migracionesSinBypassRls() throws Exception {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] migraciones = resolver.getResources("classpath*:db/migration/*.sql");
    assertTrue(migraciones.length > 0, "No se encontraron migraciones en el classpath");
    List<String> hallazgos = new ArrayList<>();
    for (Resource migracion : migraciones) {
      String sql;
      try (InputStream in = migracion.getInputStream()) {
        sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      }
      String mayusculas = sql.toUpperCase();
      if (mayusculas.contains("BYPASSRLS") || mayusculas.contains("SUPERUSER")
          || mayusculas.contains("CREATE ROLE") || mayusculas.contains("ALTER ROLE")) {
        hallazgos.add(migracion.getFilename());
      }
    }
    assertTrue(hallazgos.isEmpty(),
        "Migraciones que tocan roles/privilegios (el rol prod lo gestiona Render sin BYPASSRLS): "
            + hallazgos);
  }

  @SuppressWarnings("unchecked")
  private void revisarNodo(String ruta, Object nodo, List<String> hallazgos) {
    if (nodo instanceof Map<?, ?> mapa) {
      for (Map.Entry<?, ?> entrada : mapa.entrySet()) {
        String clave = String.valueOf(entrada.getKey());
        revisarNodo(ruta.isEmpty() ? clave : ruta + "." + clave, entrada.getValue(), hallazgos);
      }
    } else if (nodo instanceof List<?> lista) {
      for (int i = 0; i < lista.size(); i++) {
        revisarNodo(ruta + "[" + i + "]", lista.get(i), hallazgos);
      }
    } else if (nodo instanceof String valor) {
      String ultimoSegmento = ruta.contains(".")
          ? ruta.substring(ruta.lastIndexOf('.') + 1)
          : ruta;
      if (SECRET_KEY_NAMES.contains(ultimoSegmento.toLowerCase())
          && !valor.isBlank()
          && !valor.strip().startsWith("${")
          && !valor.startsWith(DEV_JWT_PREFIX)) {
        hallazgos.add(ruta + "='" + valor + "'");
      }
    }
  }
}
