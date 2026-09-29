package com.julio.odentix.odentix_backend.notification.controller;

import com.julio.odentix.odentix_backend.notification.dto.NotificationResponse;
import com.julio.odentix.odentix_backend.notification.dto.SendMessageRequest;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Historial de intentos de notificación del tenant activo.
 *
 * <p>Vista de estado para depurar entregas de WhatsApp/email sin acceder
 * a los logs del backend. Roles operativos amplios: quien confirma citas
 * es quien depura sus envíos en la práctica diaria.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notificaciones", description = "Historial de intentos de envío: estado y detalle de error.")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

  private static final String ROLES_OPERATIVOS =
      "hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')";

  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  /**
   * Lista el historial de intentos, del más reciente al más antiguo.
   */
  @GetMapping
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Listar notificaciones",
      description = "Historial paginado de intentos de envío del tenant activo, "
          + "ordenado por fecha descendente, con su estado y detalle de error."
  )
  public ResponseEntity<Page<NotificationResponse>> listarNotificaciones(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
      Pageable pageable) {
    return ResponseEntity.ok(notificationService.listarNotificaciones(pageable));
  }

  /**
   * Envía un mensaje ya revisado por un humano para una cita.
   *
   * <p>El destinatario se resuelve en el servidor desde el paciente de la
   * cita: el cliente aporta el texto, nunca a quién se envía. Sin contacto
   * queda {@code fallida} con su detalle, nunca una excepción.
   */
  @PostMapping("/send")
  @PreAuthorize("@subscriptionService.requireFeature('ai_assistant') and hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Enviar mensaje revisado",
      description = "Envía por el canal indicado el mensaje ya revisado para una "
          + "cita. El destinatario sale del paciente de la cita."
  )
  public ResponseEntity<NotificationResponse> enviarMensajeRevisado(
      @Valid @RequestBody SendMessageRequest request) {
    Notification intento = notificationService.enviarMensajeRevisado(
        request.getAppointmentId(), request.getChannel(),
        request.getSubject(), request.getBody());
    return ResponseEntity.ok(NotificationResponse.fromEntity(intento));
  }
}
