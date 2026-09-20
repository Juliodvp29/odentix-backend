package com.julio.odentix.odentix_backend.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.OutputStreamAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import net.logstash.logback.encoder.LogstashEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * El layout JSON de prod serializa el `tenant_id` del MDC como campo (FASE12-04).
 *
 * <p>Así los logs de producción se filtran por clínica sin exponer datos de
 * otras: el campo viaja en cada línea y el DoD se cumple con un simple filtro
 * `tenant_id=...` en Render/Datadog/CloudWatch.
 */
class TenantLogLayoutTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @AfterEach
  void limpiar() {
    MDC.clear();
  }

  @Test
  @DisplayName("El JSON incluye tenant_id del MDC y el mensaje intacto")
  void jsonIncluyeTenantId() throws Exception {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    Logger logger = context.getLogger("tenant-log-test");
    logger.setAdditive(false);
    logger.setLevel(Level.INFO);

    ByteArrayOutputStream salida = new ByteArrayOutputStream();
    LogstashEncoder encoder = new LogstashEncoder();
    encoder.setContext(context);
    encoder.start();
    OutputStreamAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
        new OutputStreamAppender<>();
    appender.setContext(context);
    appender.setEncoder(encoder);
    appender.setOutputStream(salida);
    appender.start();
    logger.addAppender(appender);

    try {
      MDC.put("tenant_id", "11111111-2222-3333-4444-555555555555");
      logger.info("cita confirmada");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
      encoder.stop();
    }

    JsonNode json = objectMapper.readTree(salida.toByteArray());
    assertEquals("11111111-2222-3333-4444-555555555555", json.get("tenant_id").asText());
    assertEquals("cita confirmada", json.get("message").asText());
  }
}
