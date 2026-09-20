package com.julio.odentix.odentix_backend.shared.exception;

/**
 * Reporta errores no controlados (HTTP 500) al sistema de observabilidad (FASE12-03).
 *
 * <p>Abstracción mínima para no acoplar el manejador global a ningún proveedor:
 * en producción la implementación envía a Sentry; en tests se sustituye por un
 * doble que permite verificar que el reporte se invocó sin red real.
 *
 * <p>Solo se reportan errores 500 — nunca 4xx (un error de validación no es un
 * fallo del sistema y reportarlo sería ruido + riesgo de PII).
 */
public interface ErrorReporter {

  /**
   * Reporta una excepción no controlada ya respondida al cliente como 500 genérico.
   *
   * @param ex la excepción original (nunca llega al cliente, solo al proveedor)
   * @param path la ruta donde ocurrió, para agrupar/contexto (sin datos de negocio)
   */
  void reportUnhandled(Exception ex, String path);
}
