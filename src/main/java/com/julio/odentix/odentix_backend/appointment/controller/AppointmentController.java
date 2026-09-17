package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateAppointmentStatusRequest;
import com.julio.odentix.odentix_backend.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para la gestión de citas de la agenda clínica (FASE3-02).
 */
@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Agenda y Citas", description = "Gestión de citas médicas y prevención de solapamiento de horarios.")
public class AppointmentController {

  private final AppointmentService appointmentService;

  public AppointmentController(AppointmentService appointmentService) {
    this.appointmentService = appointmentService;
  }

  /**
   * Agenda una nueva cita médica.
   *
   * @param request datos de agendamiento.
   * @return cita creada con código HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Agendar cita",
      description = "Crea una cita médica para un paciente y profesional. Impide solapamientos de horario mediante restricción de base de datos."
  )
  public ResponseEntity<AppointmentResponse> createAppointment(
      @Valid @RequestBody CreateAppointmentRequest request) {
    AppointmentResponse response = appointmentService.createAppointment(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Consulta la agenda de un rango de fechas, opcionalmente filtrada por
   * profesional, ordenada por hora de inicio.
   *
   * @param from inicio del rango en formato ISO-8601.
   * @param to fin del rango en formato ISO-8601.
   * @param professionalId filtro opcional por profesional del tenant activo.
   * @return citas del rango en orden ascendente de inicio.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Consultar agenda",
      description = "Devuelve las citas del tenant activo que inician dentro del rango, ordenadas por hora de inicio. Permite filtrar por profesional."
  )
  public ResponseEntity<List<AppointmentResponse>> listAppointments(
      @RequestParam Instant from,
      @RequestParam Instant to,
      @RequestParam(required = false) UUID professionalId) {
    return ResponseEntity.ok(appointmentService.listAppointments(from, to, professionalId));
  }

  /**
   * Cambia el estado de una cita validando que la transición sea permitida.
   *
   * @param id identificador de la cita dentro del tenant activo.
   * @param request estado destino deseado.
   * @return cita actualizada.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Cambiar estado de la cita",
      description = "Avanza la cita por su ciclo de vida (programada → confirmada → atendida / no_show / cancelada). Las transiciones inválidas devuelven 400."
  )
  public ResponseEntity<AppointmentResponse> updateStatus(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateAppointmentStatusRequest request) {
    return ResponseEntity.ok(appointmentService.updateStatus(id, request));
  }
}
