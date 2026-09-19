package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.AppointmentValueSummary;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateAppointmentStatusRequest;
import com.julio.odentix.odentix_backend.appointment.dto.WaitlistEntryResponse;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.event.AppointmentCancelledEvent;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión de citas odontológicas (FASE3-02, FASE3-04, FASE3-05, FASE3-07).
 */
@Service
public class AppointmentService {

  private final AppointmentRepository appointmentRepository;
  private final PatientRepository patientRepository;
  private final ProfessionalRepository professionalRepository;
  private final RoomRepository roomRepository;
  private final WaitlistEntryRepository waitlistEntryRepository;
  private final NotificationService notificationService;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Transiciones de estado permitidas (FASE3-04, sección 8.6 del doc de
   * arquitectura). Máquina de estado simple en el service layer, sin
   * librerías: solo avance hacia adelante, los estados finales
   * ({@code atendida}, {@code no_show}, {@code cancelada}) no salen a
   * ningún otro. Reprogramar es crear una cita nueva, no un cambio de estado.
   */
  private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSICIONES_PERMITIDAS;

  static {
    Map<AppointmentStatus, Set<AppointmentStatus>> transiciones = new EnumMap<>(AppointmentStatus.class);
    transiciones.put(AppointmentStatus.programada, Set.of(AppointmentStatus.confirmada, AppointmentStatus.cancelada));
    transiciones.put(AppointmentStatus.confirmada,
        Set.of(AppointmentStatus.atendida, AppointmentStatus.no_show, AppointmentStatus.cancelada));
    transiciones.put(AppointmentStatus.atendida, Set.of());
    transiciones.put(AppointmentStatus.no_show, Set.of());
    transiciones.put(AppointmentStatus.cancelada, Set.of());
    TRANSICIONES_PERMITIDAS = Map.copyOf(transiciones);
  }

  public AppointmentService(
      AppointmentRepository appointmentRepository,
      PatientRepository patientRepository,
      ProfessionalRepository professionalRepository,
      RoomRepository roomRepository,
      WaitlistEntryRepository waitlistEntryRepository,
      NotificationService notificationService,
      ApplicationEventPublisher eventPublisher) {
    this.appointmentRepository = appointmentRepository;
    this.patientRepository = patientRepository;
    this.professionalRepository = professionalRepository;
    this.roomRepository = roomRepository;
    this.waitlistEntryRepository = waitlistEntryRepository;
    this.notificationService = notificationService;
    this.eventPublisher = eventPublisher;
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

    // FASE8-04: avisar que la cita quedó agendada. El servicio de
    // notificaciones nunca lanza: si el proveedor falla, queda registrada
    // como fallida sin revertir la creación de la cita.
    notificationService.sendAppointmentScheduled(saved.getId());

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

  /**
   * Cambia el estado de una cita validando la transición (FASE3-04).
   *
   * @param id identificador de la cita dentro del tenant activo.
   * @param request estado destino deseado.
   * @return cita actualizada.
   * @throws ResourceNotFoundException si la cita no existe en el tenant activo.
   * @throws IllegalArgumentException si la transición no es válida.
   */
  @Transactional
  public AppointmentResponse updateStatus(UUID id, UpdateAppointmentStatusRequest request) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();

    Appointment appointment = appointmentRepository.findByIdAndTenantId(id, currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada: " + id));

    AppointmentStatus actual = appointment.getStatus();
    AppointmentStatus destino = request.getStatus();

    // Mismo estado: no-op idempotente para que los reintentos no fallen.
    if (!actual.equals(destino)) {
      Set<AppointmentStatus> permitidos =
          TRANSICIONES_PERMITIDAS.getOrDefault(actual, Set.of());
      if (!permitidos.contains(destino)) {
        String detalle = permitidos.isEmpty()
            ? "ninguna (es un estado final)"
            : "solo " + permitidos;
        throw new IllegalArgumentException(
            "No se puede pasar de '" + actual + "' a '" + destino
                + "'. Transiciones permitidas desde '" + actual + "': " + detalle);
      }
      appointment.setStatus(destino);
    }

    Appointment saved = appointmentRepository.save(appointment);
    AppointmentResponse response = AppointmentResponse.fromEntity(saved);

    // FASE3-07: Si la cita pasa a cancelada, calcular candidatos compatibles de la lista de espera
    // para sugerir la recuperación del espacio liberado.
    if (destino == AppointmentStatus.cancelada) {
      response.setWaitlistCandidates(findCandidatesForAppointment(saved, currentTenantId));
    }

    // FASE8-06: en transición real a cancelada, publicar el evento para que la
    // automatización de recuperación (módulo task) cree la tarea si aplica.
    // Solo en transición real, no en no-op idempotente.
    if (destino == AppointmentStatus.cancelada && !actual.equals(destino)) {
      eventPublisher.publishEvent(
          new AppointmentCancelledEvent(saved.getId(), currentTenantId));
    }

    // FASE8-04: al confirmar, enviar la confirmación por email. Solo en
    // transición real (no en no-op idempotente) para no reenviar en reintentos.
    // Nunca lanza: un fallo del proveedor no revierte el cambio de estado.
    if (destino == AppointmentStatus.confirmada && !actual.equals(destino)) {
      notificationService.sendAppointmentConfirmation(saved.getId());
    }

    return response;
  }

  /**
   * Obtiene los candidatos compatibles de la lista de espera para el espacio
   * de una cita (FASE3-07).
   *
   * @param appointmentId identificador de la cita dentro del tenant activo.
   * @return lista de candidatos compatibles en formato {@link WaitlistEntryResponse}.
   * @throws ResourceNotFoundException si la cita no existe en el tenant activo.
   */
  @Transactional(readOnly = true)
  public List<WaitlistEntryResponse> findWaitlistCandidates(UUID appointmentId) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();
    Appointment appointment = appointmentRepository.findByIdAndTenantId(appointmentId, currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada: " + appointmentId));

    return findCandidatesForAppointment(appointment, currentTenantId);
  }

  private List<WaitlistEntryResponse> findCandidatesForAppointment(Appointment appointment, UUID tenantId) {
    UUID patientId = appointment.getPatient() != null ? appointment.getPatient().getId() : null;
    return waitlistEntryRepository.findCompatibleCandidates(
            tenantId,
            WaitlistStatus.activa,
            patientId,
            appointment.getProcedureId(),
            appointment.getStartsAt(),
            appointment.getEndsAt())
        .stream()
        .map(WaitlistEntryResponse::fromEntity)
        .toList();
  }

  /**
   * Suma del valor estimado de la agenda en un rango (FASE3-05).
   *
   * <p>Excluye citas {@code cancelada} y {@code no_show}: son espacio
   * liberado reutilizable (FASE3-02), y contarlas duplicaría el valor si
   * el horario se reagenda. Insumo para reportes y Fase 9, sin lógica de
   * riesgo aquí.
   *
   * @param from inicio del rango (requerido).
   * @param to fin del rango (requerido, no anterior a {@code from}).
   * @return total estimado y conteo de citas del rango.
   * @throws IllegalArgumentException si el rango es inválido.
   */
  @Transactional(readOnly = true)
  public AppointmentValueSummary summarizeValue(Instant from, Instant to) {
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("La fecha de inicio del rango debe ser anterior o igual a la de fin");
    }

    UUID currentTenantId = TenantContext.getRequiredTenantId();
    Object[] resultado = appointmentRepository.sumAndCountByTenantAndRange(
            currentTenantId, from, to, List.of(AppointmentStatus.cancelada, AppointmentStatus.no_show))
        .getFirst();

    // El total llega como Number (BigDecimal normalmente, pero el proveedor
    // puede devolver Long/Integer cuando el rango está vacío): convertir
    // por texto evita ClassCastException. COALESCE garantiza no-null.
    BigDecimal total = new BigDecimal(resultado[0].toString());
    long conteo = ((Number) resultado[1]).longValue();
    return new AppointmentValueSummary(from, to, total, conteo);
  }
}
