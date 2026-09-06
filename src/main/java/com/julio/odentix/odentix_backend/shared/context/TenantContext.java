package com.julio.odentix.odentix_backend.shared.context;

import java.util.UUID;

/**
 * Almacena y provee acceso al ID del tenant actual durante el ciclo de vida
 * de una petición HTTP (FASE1-08).
 *
 * Utiliza {@link ThreadLocal} para aislar el contexto por hilo de ejecución.
 * Debe limpiarse siempre al finalizar la petición (ej. en bloque finally de un filtro)
 * para evitar fugas de memoria o retención de tenant_id en hilos reutilizados del pool.
 */
public final class TenantContext {

  private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

  private TenantContext() {
    throw new UnsupportedOperationException("Clase utilitaria, no instanciable");
  }

  /**
   * Establece el tenant_id en el hilo actual.
   *
   * @param tenantId identificador único del tenant
   */
  public static void setTenantId(UUID tenantId) {
    CURRENT_TENANT.set(tenantId);
  }

  /**
   * Obtiene el tenant_id asociado al hilo actual, o {@code null} si no se ha establecido.
   *
   * @return UUID del tenant actual o null
   */
  public static UUID getTenantId() {
    return CURRENT_TENANT.get();
  }

  /**
   * Obtiene el tenant_id asociado al hilo actual o lanza {@link IllegalStateException}
   * si no existe un tenant configurado en el contexto.
   *
   * @return UUID del tenant actual
   * @throws IllegalStateException si no hay tenant_id en el contexto
   */
  public static UUID getRequiredTenantId() {
    UUID tenantId = CURRENT_TENANT.get();
    if (tenantId == null) {
      throw new IllegalStateException("No hay tenant_id configurado en el TenantContext para el hilo actual");
    }
    return tenantId;
  }

  /**
   * Limpia el tenant_id del hilo actual.
   * Llama a {@link ThreadLocal#remove()} para evitar memory leaks en pools de hilos (Tomcat).
   */
  public static void clear() {
    CURRENT_TENANT.remove();
  }
}
