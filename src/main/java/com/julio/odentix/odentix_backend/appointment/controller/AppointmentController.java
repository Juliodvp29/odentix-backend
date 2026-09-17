package com.julio.odentix.odentix_backend.appointment.controller;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
