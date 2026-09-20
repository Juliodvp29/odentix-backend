package com.julio.odentix.odentix_backend.shared.exception;

import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import io.sentry.Sentry;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Implementación de {@link ErrorReporter} sobre Sentry (FASE12-03).
 *
 * <p>Sin `SENTRY_DSN` configurado el SDK queda inactivo y `captureException` es
 * un no-op — dev/test no envían nada. Con DSN (prod), el evento incluye el
 * stacktrace y el `tenant_id` como tag para filtrar errores por clínica
 * (nunca payloads ni datos de pacientes: `send-default-pii` queda en false).
 */
@Service
public class SentryErrorReporter implements ErrorReporter {

  private static final Logger log = LoggerFactory.getLogger(SentryErrorReporter.class);

  @Override
  public void reportUnhandled(Exception ex, String path) {
    // El tenant actual como tag (no como dato): permite filtrar en Sentry por
    // clínica sin exponer datos entre tenants (aislamiento multi-tenant). Si no hay tenant
    // atribuible, el error se reporta igual, sin tag.
    UUID tenantId = TenantContext.getTenantId();
    if (tenantId != null) {
      Sentry.setTag("tenant_id", tenantId.toString());
    }
    Sentry.captureException(ex);
    log.debug("Error no controlado en {} reportado a Sentry.", path);
  }
}

