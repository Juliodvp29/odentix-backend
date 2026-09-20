package com.julio.odentix.odentix_backend.shared.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Pruebas unitarias del cableado de observabilidad en el manejador global (FASE12-03).
 *
 * <p>Solo los errores no controlados (500) llegan al {@link ErrorReporter} —
 * los 4xx tienen su handler propio y reportarlos sería ruido. El cliente
 * siempre recibe el cuerpo genérico, nunca el detalle interno.
 */
class GlobalExceptionHandlerTest {

  private static class StubErrorReporter implements ErrorReporter {
    final List<Exception> reportados = new ArrayList<>();
    final List<String> rutas = new ArrayList<>();

    @Override
    public void reportUnhandled(Exception ex, String path) {
      reportados.add(ex);
      rutas.add(path);
    }
  }

  @Test
  @DisplayName("Una ruta inexistente es 404 y no llega al ErrorReporter")
  void rutaInexistenteEs404SinReportar() {
    StubErrorReporter stub = new StubErrorReporter();
    GlobalExceptionHandler handler = new GlobalExceptionHandler(stub);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/noexiste");
    NoResourceFoundException causa = new NoResourceFoundException(
        HttpMethod.GET, "/api/v1/noexiste", "recurso estático inexistente");

    ResponseEntity<ApiErrorResponse> respuesta = handler.handleNoResourceFound(causa, request);

    assertEquals(HttpStatus.NOT_FOUND, respuesta.getStatusCode());
    assertTrue(stub.reportados.isEmpty());
  }

  @Test
  @DisplayName("Un 500 reporta al ErrorReporter y responde el cuerpo genérico")
  void errorNoControladoReportaYRespondeGenerico() {
    StubErrorReporter stub = new StubErrorReporter();
    GlobalExceptionHandler handler = new GlobalExceptionHandler(stub);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");
    RuntimeException causa = new RuntimeException("detalle interno de BD");

    ResponseEntity<ApiErrorResponse> respuesta = handler.handleUncaughtException(causa, request);

    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, respuesta.getStatusCode());
    assertTrue(respuesta.getBody() != null
        && "Ocurrió un error interno en el servidor".equals(respuesta.getBody().getMessage()));
    assertEquals(1, stub.reportados.size());
    assertTrue(stub.reportados.get(0) == causa);
    assertEquals(List.of("/api/v1/patients"), stub.rutas);
  }

  @Test
  @DisplayName("Un 400 no llega al ErrorReporter")
  void errorDeClienteNoReporta() {
    StubErrorReporter stub = new StubErrorReporter();
    GlobalExceptionHandler handler = new GlobalExceptionHandler(stub);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/patients");

    ResponseEntity<ApiErrorResponse> respuesta =
        handler.handleBadRequest(new IllegalArgumentException("dato inválido"), request);

    assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
    assertTrue(stub.reportados.isEmpty());
  }
}
