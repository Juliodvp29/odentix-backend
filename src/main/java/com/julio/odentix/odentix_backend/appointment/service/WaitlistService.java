package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.CreateWaitlistEntryRequest;
import com.julio.odentix.odentix_backend.appointment.dto.WaitlistEntryResponse;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la lista de espera (FASE3-06).
 */
@Service
public class WaitlistService {

  private final WaitlistEntryRepository waitlistEntryRepository;
  private final PatientRepository patientRepository;

  public WaitlistService(
      WaitlistEntryRepository waitlistEntryRepository,
      PatientRepository patientRepository) {
    this.waitlistEntryRepository = waitlistEntryRepository;
    this.patientRepository = patientRepository;
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
    Patient patient = patientRepository.findById(request.getPatientId())
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + request.getPatientId()));

    WaitlistEntry entry = new WaitlistEntry(currentTenantId, patient);
    entry.setProcedureId(request.getProcedureId());
    entry.setDesiredFrom(request.getDesiredFrom());
    entry.setDesiredTo(request.getDesiredTo());

    return WaitlistEntryResponse.fromEntity(waitlistEntryRepository.save(entry));
  }
}
