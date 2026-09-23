package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.ConvertWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateWaitlistStatusRequest;
import com.julio.odentix.odentix_backend.appointment.dto.WaitlistEntryResponse;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Servicio de negocio para la lista de espera (FASE3-06).
 */
@Service
public class WaitlistService {

  private static final Map<WaitlistStatus, Set<WaitlistStatus>> TRANSICIONES_PERMITIDAS;

  static {
    Map<WaitlistStatus, Set<WaitlistStatus>> transiciones = new EnumMap<>(WaitlistStatus.class);
    transiciones.put(
        WaitlistStatus.activa,
        Set.of(WaitlistStatus.contactado, WaitlistStatus.descartada));
    transiciones.put(WaitlistStatus.contactado, Set.of(WaitlistStatus.descartada));
    transiciones.put(WaitlistStatus.convertida, Set.of());
    transiciones.put(WaitlistStatus.descartada, Set.of());
    TRANSICIONES_PERMITIDAS = Map.copyOf(transiciones);
  }

  private final WaitlistEntryRepository waitlistEntryRepository;
  private final AppointmentRepository appointmentRepository;
  private final PatientRepository patientRepository;
  private final AppointmentService appointmentService;

  public WaitlistService(
      WaitlistEntryRepository waitlistEntryRepository,
      AppointmentRepository appointmentRepository,
      PatientRepository patientRepository,
      AppointmentService appointmentService) {
    this.waitlistEntryRepository = waitlistEntryRepository;
    this.appointmentRepository = appointmentRepository;
    this.patientRepository = patientRepository;
    this.appointmentService = appointmentService;
  }

  /**
   * Registra a un paciente en lista de espera.
   *
   * @param request paciente, procedimiento de interés y rango deseado.
   * @return entrada creada, siempre en estado {@code activa}.
   * @throws ResourceNotFoundException si el paciente no existe en el tenant activo.
   * @throws IllegalArgumentException si el rango deseado es inválido.
   */
  @Transactional
  public WaitlistEntryResponse addEntry(CreateWaitlistEntryRequest request) {
    if (request.getDesiredFrom() != null
        && request.getDesiredTo() != null
        && request.getDesiredFrom().isAfter(request.getDesiredTo())) {
      throw new IllegalArgumentException(
          "La fecha de inicio del rango deseado debe ser anterior o igual a la de fin");
    }

    UUID currentTenantId = TenantContext.getRequiredTenantId();

    // Misma validación que AppointmentService: el filtro @TenantId hace que
    // un paciente de otro tenant resulte invisible (404, no 403).
    Patient patient = patientRepository.findByIdAndTenantId(request.getPatientId(), currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + request.getPatientId()));

    WaitlistEntry entry = new WaitlistEntry(currentTenantId, patient);
    entry.setProcedureId(request.getProcedureId());
    entry.setDesiredFrom(request.getDesiredFrom());
    entry.setDesiredTo(request.getDesiredTo());

    return WaitlistEntryResponse.fromEntity(waitlistEntryRepository.save(entry));
  }

  @Transactional(readOnly = true)
  public Page<WaitlistEntryResponse> listEntries(
      WaitlistStatus status, String query, Pageable pageable) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();
    String normalizedQuery = query == null || query.isBlank() ? null : query.trim();
    Sort.Order requestedCreatedAt = pageable.getSort().getOrderFor("createdAt");
    Sort stableSort = Sort.by(
        requestedCreatedAt == null ? Sort.Order.desc("createdAt") : requestedCreatedAt,
        Sort.Order.asc("id"));
    PageRequest stablePageable = PageRequest.of(
        pageable.getPageNumber(), pageable.getPageSize(), stableSort);

    Page<WaitlistEntry> page;
    if (status == null && normalizedQuery == null) {
      page = waitlistEntryRepository.searchAll(currentTenantId, stablePageable);
    } else if (status != null && normalizedQuery == null) {
      page = waitlistEntryRepository.searchByStatus(currentTenantId, status, stablePageable);
    } else if (status == null) {
      page = waitlistEntryRepository.searchByText(
          currentTenantId, normalizedQuery, stablePageable);
    } else {
      page = waitlistEntryRepository.searchByStatusAndText(
          currentTenantId, status, normalizedQuery, stablePageable);
    }
    return page.map(WaitlistEntryResponse::fromEntity);
  }

  @Transactional(readOnly = true)
  public WaitlistEntryResponse getById(UUID id) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();
    WaitlistEntry entry = waitlistEntryRepository
        .findWithPatientByIdAndTenantId(id, currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Entrada de lista de espera no encontrada: " + id));
    return WaitlistEntryResponse.fromEntity(entry);
  }

  @Transactional
  public WaitlistEntryResponse updateStatus(UUID id, UpdateWaitlistStatusRequest request) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();
    WaitlistEntry entry = waitlistEntryRepository
        .findWithPatientByIdAndTenantIdForUpdate(id, currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Entrada de lista de espera no encontrada: " + id));

    WaitlistStatus current = entry.getStatus();
    WaitlistStatus target = request.getStatus();
    if (current == target) {
      return WaitlistEntryResponse.fromEntity(entry);
    }

    Set<WaitlistStatus> allowed = TRANSICIONES_PERMITIDAS.getOrDefault(current, Set.of());
    if (!allowed.contains(target)) {
      String detail = allowed.isEmpty()
          ? "ninguna (es un estado final)"
          : String.join(", ", allowed.stream().map(Enum::name).toList());
      throw new ConflictException(
          "No se puede pasar de '" + current + "' a '" + target
              + "'. Transiciones permitidas desde '" + current + "': " + detail);
    }

    boolean hasDiscardReason = request.getDiscardReason() != null
        && !request.getDiscardReason().isBlank();
    if (hasDiscardReason && target != WaitlistStatus.descartada) {
      throw new IllegalArgumentException("El motivo de descarte solo se admite al estado descartada");
    }

    Instant now = Instant.now();
    if (target == WaitlistStatus.contactado) {
      entry.setContactedAt(now);
    } else if (target == WaitlistStatus.descartada) {
      entry.setDiscardedAt(now);
      entry.setDiscardReason(hasDiscardReason ? request.getDiscardReason().trim() : null);
    }
    entry.setStatus(target);
    return WaitlistEntryResponse.fromEntity(waitlistEntryRepository.save(entry));
  }

  @Transactional
  public ConversionResult convert(UUID id, ConvertWaitlistEntryRequest request) {
    UUID currentTenantId = TenantContext.getRequiredTenantId();
    WaitlistEntry entry = waitlistEntryRepository
        .findWithPatientByIdAndTenantIdForUpdate(id, currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Entrada de lista de espera no encontrada: " + id));

    if (entry.getStatus() == WaitlistStatus.convertida) {
      if (entry.getConvertedAppointment() == null) {
        throw new ConflictException(
            "La entrada está convertida pero no tiene una cita asociada");
      }
      Appointment existing = appointmentRepository
          .findByIdAndTenantId(entry.getConvertedAppointment().getId(), currentTenantId)
          .orElseThrow(() -> new ConflictException(
              "La cita asociada a la entrada convertida ya no existe"));
      return new ConversionResult(AppointmentResponse.fromEntity(existing), false);
    }

    if (entry.getStatus() == WaitlistStatus.descartada) {
      throw new ConflictException("Una entrada descartada no se puede convertir en una cita");
    }
    if (entry.getStatus() != WaitlistStatus.activa
        && entry.getStatus() != WaitlistStatus.contactado) {
      throw new ConflictException("La entrada no está disponible para conversión");
    }

    Appointment source = appointmentRepository
        .findByIdAndTenantId(request.getSourceAppointmentId(), currentTenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cita origen no encontrada: " + request.getSourceAppointmentId()));
    if (source.getStatus() != AppointmentStatus.cancelada) {
      throw new IllegalArgumentException("La cita origen debe estar cancelada");
    }

    validateCompatibility(entry, source);

    CreateAppointmentRequest appointmentRequest = new CreateAppointmentRequest();
    appointmentRequest.setPatientId(entry.getPatient().getId());
    if (source.getProfessional() != null) {
      appointmentRequest.setProfessionalId(source.getProfessional().getId());
    }
    if (source.getRoom() != null) {
      appointmentRequest.setRoomId(source.getRoom().getId());
    }
    appointmentRequest.setProcedureId(source.getProcedureId());
    appointmentRequest.setStartsAt(source.getStartsAt());
    appointmentRequest.setEndsAt(source.getEndsAt());
    appointmentRequest.setNotes(request.getNotes());

    AppointmentResponse created = appointmentService.createAppointmentWithoutNotification(appointmentRequest);
    Appointment convertedAppointment = appointmentRepository
        .findByIdAndTenantId(created.getId(), currentTenantId)
        .orElseThrow(() -> new ConflictException("La cita convertida no pudo ser persistida"));

    entry.setConvertedAppointment(convertedAppointment);
    entry.setRecoveredFromAppointment(source);
    entry.setConvertedAt(Instant.now());
    entry.setStatus(WaitlistStatus.convertida);
    waitlistEntryRepository.saveAndFlush(entry);
    scheduleAppointmentNotification(convertedAppointment.getId());

    return new ConversionResult(created, true);
  }

  private void scheduleAppointmentNotification(UUID appointmentId) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      appointmentService.notifyAppointmentScheduled(appointmentId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        appointmentService.notifyAppointmentScheduled(appointmentId);
      }
    });
  }

  private void validateCompatibility(WaitlistEntry entry, Appointment source) {
    if (entry.getProcedureId() != null
        && source.getProcedureId() != null
        && !entry.getProcedureId().equals(source.getProcedureId())) {
      throw new IllegalArgumentException(
          "El procedimiento de la cita origen no es compatible con la entrada");
    }
    if (entry.getDesiredFrom() != null
        && !entry.getDesiredFrom().isBefore(source.getEndsAt())) {
      throw new IllegalArgumentException(
          "La cita origen termina antes del inicio del rango deseado");
    }
    if (entry.getDesiredTo() != null
        && !entry.getDesiredTo().isAfter(source.getStartsAt())) {
      throw new IllegalArgumentException(
          "La cita origen comienza después del fin del rango deseado");
    }
  }

  public record ConversionResult(AppointmentResponse appointment, boolean created) {
  }
}
