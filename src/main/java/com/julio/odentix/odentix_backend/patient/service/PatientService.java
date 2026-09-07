package com.julio.odentix.odentix_backend.patient.service;

import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.dto.UpdatePatientRequest;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lógica de pacientes (FASE2-02).
 *
 * <p>El tenant siempre sale del {@code TenantContext} del usuario
 * autenticado, nunca del request: así un tenant no puede leer ni modificar
 * pacientes de otro ni siquiera conociendo su ID directo (responde 404,
 * para ni confirmar que existen — regla §5 de AGENTS.md).
 *
 * <p>Baja lógica: un paciente inactivo es invisible para la API (GET, PATCH
 * y DELETE responden 404); la fila se conserva para las claves foráneas
 * futuras (citas, planes, facturas).
 */
@Service
public class PatientService {

  private final PatientRepository patientRepository;

  public PatientService(PatientRepository patientRepository) {
    this.patientRepository = patientRepository;
  }

  @Transactional
  public PatientResponse create(CreatePatientRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Patient patient = new Patient(tenantId, request.getFirstName(), request.getLastName());
    patient.setDocumentType(request.getDocumentType());
    patient.setDocumentNumber(request.getDocumentNumber());
    patient.setBirthDate(request.getBirthDate());
    patient.setPhone(request.getPhone());
    patient.setEmail(request.getEmail());
    patient.setAddress(request.getAddress());
    patient.setEmergencyContactName(request.getEmergencyContactName());
    patient.setEmergencyContactPhone(request.getEmergencyContactPhone());

    return PatientResponse.fromEntity(patientRepository.save(patient));
  }

  @Transactional(readOnly = true)
  public PatientResponse getById(UUID id) {
    return PatientResponse.fromEntity(findActiveOrThrow(id));
  }

  @Transactional
  public PatientResponse patch(UUID id, UpdatePatientRequest request) {
    Patient patient = findActiveOrThrow(id);

    if (request.getFirstName() != null) {
      patient.setFirstName(request.getFirstName());
    }
    if (request.getLastName() != null) {
      patient.setLastName(request.getLastName());
    }
    if (request.getDocumentType() != null) {
      patient.setDocumentType(request.getDocumentType());
    }
    if (request.getDocumentNumber() != null) {
      patient.setDocumentNumber(request.getDocumentNumber());
    }
    if (request.getBirthDate() != null) {
      patient.setBirthDate(request.getBirthDate());
    }
    if (request.getPhone() != null) {
      patient.setPhone(request.getPhone());
    }
    if (request.getEmail() != null) {
      patient.setEmail(request.getEmail());
    }
    if (request.getAddress() != null) {
      patient.setAddress(request.getAddress());
    }
    if (request.getEmergencyContactName() != null) {
      patient.setEmergencyContactName(request.getEmergencyContactName());
    }
    if (request.getEmergencyContactPhone() != null) {
      patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
    }

    return PatientResponse.fromEntity(patientRepository.save(patient));
  }

  @Transactional
  public void deactivate(UUID id) {
    Patient patient = findActiveOrThrow(id);
    patient.setActive(false);
    patientRepository.save(patient);
  }

  private Patient findActiveOrThrow(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    // Filtro por tenantId explícito además del automático @TenantId
    // (defensa en profundidad, regla §5.2 de AGENTS.md).
    return patientRepository.findByIdAndTenantId(id, tenantId)
        .filter(Patient::isActive)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paciente no encontrado"));
  }
}
