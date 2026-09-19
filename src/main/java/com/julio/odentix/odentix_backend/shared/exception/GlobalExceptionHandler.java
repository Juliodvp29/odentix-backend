package com.julio.odentix.odentix_backend.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Manejador global centralizado de excepciones (FASE2-03).
 *
 * <p>Estandariza los códigos HTTP y los cuerpos de respuesta de error para todos los módulos
 * del backend mediante {@link ApiErrorResponse}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleResourceNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.NOT_FOUND.value(),
        HttpStatus.NOT_FOUND.getReasonPhrase(),
        ex.getMessage(),
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatus(
      ResponseStatusException ex, HttpServletRequest request) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    ApiErrorResponse error = new ApiErrorResponse(
        status.value(),
        status.getReasonPhrase(),
        message,
        request.getRequestURI());
    return ResponseEntity.status(status).body(error);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.toMap(
            FieldError::getField,
            error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Inválido",
            (first, second) -> first));

    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "Error de validación en la solicitud",
        request.getRequestURI(),
        fieldErrors);
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiErrorResponse> handleNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    // Cuerpo malformado o valor inválido para un enum (ej. entryType
    // inexistente): 400, nunca el 500 genérico del manejador global.
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        "El cuerpo de la solicitud es inválido o tiene un formato incorrecto",
        request.getRequestURI());
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleDataIntegrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    String message = "Ya existe un registro con esos datos o se violó una restricción de integridad";

    String detail = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
    if (detail != null && detail.contains("no_overlapping_appointments")) {
      message = "El profesional ya cuenta con una cita programada que se solapa con el horario seleccionado";
    }

    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        message,
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiErrorResponse> handleBadCredentials(
      BadCredentialsException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.UNAUTHORIZED.value(),
        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
        "Credenciales inválidas",
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.FORBIDDEN.value(),
        "Acceso denegado",
        "No tienes permisos suficientes para acceder a este recurso",
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
  }
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiErrorResponse> handleConflict(
      ConflictException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.CONFLICT.value(),
        HttpStatus.CONFLICT.getReasonPhrase(),
        ex.getMessage(),
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
  }

  @ExceptionHandler(com.julio.odentix.odentix_backend.assistant.AssistantException.class)
  public ResponseEntity<ApiErrorResponse> handleAssistant(
      RuntimeException ex, HttpServletRequest request) {
    // 502, no 500: el backend está bien, el que falló fue el proveedor de IA
    // (sin key, timeout o rechazo). El fallback a plantilla fija llega en FASE10-03.
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.BAD_GATEWAY.value(),
        HttpStatus.BAD_GATEWAY.getReasonPhrase(),
        ex.getMessage(),
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<ApiErrorResponse> handleBadRequest(
      RuntimeException ex, HttpServletRequest request) {
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        ex.getMessage(),
        request.getRequestURI());
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler({
      MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleBadQueryParam(
      Exception ex, HttpServletRequest request) {
    // Parámetro requerido ausente o con formato inválido (ej. fecha no
    // ISO-8601): 400 con el formato estándar, no un 500 genérico.
    String message = ex instanceof MissingServletRequestParameterException missing
        ? "Falta el parámetro requerido: " + missing.getParameterName()
        : "El parámetro '" + ((MethodArgumentTypeMismatchException) ex).getName()
            + "' tiene un formato inválido";
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.BAD_REQUEST.value(),
        HttpStatus.BAD_REQUEST.getReasonPhrase(),
        message,
        request.getRequestURI());
    return ResponseEntity.badRequest().body(error);
  }

  @ExceptionHandler(com.julio.odentix.odentix_backend.shared.storage.exception.StorageException.class)
  public ResponseEntity<ApiErrorResponse> handleStorageException(
      com.julio.odentix.odentix_backend.shared.storage.exception.StorageException ex,
      HttpServletRequest request) {
    log.error("Error de almacenamiento en {}: {}", request.getRequestURI(), ex.getMessage(), ex);
    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
        "Error en el almacenamiento de archivos",
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUncaughtException(
      Exception ex, HttpServletRequest request) {
    // Loguear el error para diagnóstico sin filtrar datos sensibles ni stack traces al cliente.
    log.error("Error no controlado procesando solicitud en {}: {}", request.getRequestURI(), ex.getMessage(), ex);

    ApiErrorResponse error = new ApiErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
        "Ocurrió un error interno en el servidor",
        request.getRequestURI());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
