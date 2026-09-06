package com.julio.odentix.odentix_backend.shared.context;

import java.util.Map;
import java.util.UUID;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.stereotype.Component;

/**
 * Resuelve el identificador del tenant actual para Hibernate a partir del {@link TenantContext} (FASE1-09).
 *
 * <p>Permite que Hibernate 6/7 filtre y asigne automáticamente el {@code tenant_id} en todas las
 * operaciones sobre entidades de negocio que extiendan {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity}.
 *
 * <p>Cuando no hay un tenant contextual (ej. durante el arranque de Spring Boot, validación de repositorios,
 * o tareas del sistema sin request), devuelve {@link #ROOT_TENANT_ID}, el cual se marca como root
 * mediante {@link #isRoot(UUID)} para permitir operaciones globales sin bloquear el SessionFactory.
 */
@Component
public class TenantIdentifierResolver
    implements CurrentTenantIdentifierResolver<UUID>, HibernatePropertiesCustomizer {

  /**
   * Identificador sentinel (nil UUID) utilizado para representar la sesión raíz/sistema cuando
   * no hay un tenant específico activo en el contexto.
   */
  public static final UUID ROOT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

  @Override
  public UUID resolveCurrentTenantIdentifier() {
    UUID tenantId = TenantContext.getTenantId();
    return tenantId != null ? tenantId : ROOT_TENANT_ID;
  }

  @Override
  public boolean validateExistingCurrentSessions() {
    return true;
  }

  @Override
  public boolean isRoot(UUID tenantId) {
    return ROOT_TENANT_ID.equals(tenantId);
  }

  @Override
  public void customize(Map<String, Object> hibernateProperties) {
    hibernateProperties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, this);
  }
}
