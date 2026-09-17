package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión de citas odontológicas (FASE3-02).
 */
@Service
public class AppointmentService {

  private final AppointmentRepository appointmentRepository;
  private final PatientRepository patientRepository;
  private final ProfessionalRepository professionalRepository;
  private final RoomRepository roomRepository;

  public AppointmentService(
      AppointmentRepository appointmentRepository,
      PatientRepository patientRepository,
      ProfessionalRepository professionalRepository,
      RoomRepository roomRepository) {
    this.appointmentRepository = appointmentRepository;
    this.patientRepository = patientRepository;
    this.professionalRepository = professionalRepository;
    this.roomRepository = roomRepository;
  }

  /**
   * Crea y persiste una nueva cita médica.
   *
   * @param request datos para la creación de la cita.
   * @return cita creada en formato {@link AppointmentResponse}.
   * @throws IllegalArgumentException si el rango temporal es inválido.
   * @throws ResourceNotFoundException si el paciente, profesional o sala no existen en el tenant activo.
   */
  @Transactional
  public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
    if (!request.getStartsAt().isBefore(request.getEndsAt())) {
      throw new IllegalArgumentException("La fecha y hora de fin debe ser posterior a la de inicio");
    }

    UUID currentTenantId = TenantContext.getTenantId();

    Patient patient = patientRepository.findById(request.getPatientId())
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + request.getPatientId()));

    Professional professional = null;
    if (request.getProfessionalId() != null) {
      professional = professionalRepository.findById(request.getProfessionalId())
          .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + request.getProfessionalId()));
    }

    Room room = null;
    if (request.getRoomId() != null) {
      room = roomRepository.findById(request.getRoomId())
          .orElseThrow(() -> new ResourceNotFoundException("Consultorio no encontrado: " + request.getRoomId()));
    }

    Appointment appointment = new Appointment();
    appointment.setTenantId(currentTenantId);
    appointment.setPatient(patient);
    appointment.setProfessional(professional);
    appointment.setRoom(room);
    appointment.setProcedureId(request.getProcedureId());
    appointment.setStartsAt(request.getStartsAt());
    appointment.setEndsAt(request.getEndsAt());
    appointment.setEstimatedValueCop(request.getEstimatedValueCop() != null ? request.getEstimatedValueCop() : BigDecimal.ZERO);
    appointment.setRiskLevel(request.getRiskLevel() != null ? request.getRiskLevel() : RiskLevel.bajo);
    appointment.setStatus(AppointmentStatus.programada);
    appointment.setNotes(request.getNotes());

    // Se usa saveAndFlush para disparar las constraints de BD (incluyendo no_overlapping_appointments)
    // de forma síncrona dentro del bloque transaccional.
    Appointment saved = appointmentRepository.saveAndFlush(appointment);
    return AppointmentResponse.fromEntity(saved);
  }

  /**
   * Consulta la agenda de un rango de fechas, opcionalmente de un solo
   * profesional, ordenada por hora de inicio (FASE3-03).
   *
   * @param from inicio del rango (requerido).
   * @param to fin del rango (requerido, no anterior a {@code from}).
   * @param professionalId filtro opcional por profesional del tenant activo.
   * @return citas del rango en orden ascendente de inicio.
   * @throws IllegalArgumentException si el rango es inválido.
   * @throws ResourceNotFoundException si el profesional no existe en el tenant activo.
   */
  @Transactional(readOnly = true)
  public List<AppointmentResponse> listAppointments(Instant from, Instant to, UUID professionalId) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("La fecha de inicio del rango debe ser anterior o igual a la de fin");
    }

    UUID currentTenantId = TenantContext.getRequiredTenantId();

    if (professionalId != null) {
      professionalRepository.findById(professionalId)
          .filter(professional -> currentTenantId.equals(professional.getTenantId()))
          .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + professionalId));
      return appointmentRepository
          .findByTenantIdAndProfessionalIdAndStartsAtBetweenOrderByStartsAtAsc(
              currentTenantId, professionalId, from, to)
          .stream()
          .map(AppointmentResponse::fromEntity)
          .toList();
    }

    return appointmentRepository
        .findByTenantIdAndStartsAtBetweenOrderByStartsAtAsc(currentTenantId, from, to)
        .stream()
        .map(AppointmentResponse::fromEntity)
        .toList();
  }
}
