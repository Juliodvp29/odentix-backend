package com.julio.odentix.odentix_backend.notification.controller;

import com.julio.odentix.odentix_backend.notification.dto.NotificationResponse;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
}
