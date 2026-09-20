package com.julio.odentix.odentix_backend.patient.service;

import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.dto.UpdatePatientRequest;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.subscription.entity.LimitKey;
import com.julio.odentix.odentix_backend.subscription.service.SubscriptionService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de pacientes (FASE2-02 / FASE2-03).
 *
 * <p>El tenant siempre sale del {@code TenantContext} del usuario
 * autenticado, nunca del request: así un tenant no puede leer ni modificar
 * pacientes de otro ni siquiera conociendo su ID directo (responde 404,
 * para ni confirmar que existen — regla de aislamiento multi-tenant del proyecto).
 *
 * <p>Baja lógica: un paciente inactivo es invisible para la API (GET, PATCH
 * y DELETE responden 404); la fila se conserva para las claves foráneas
 * futuras (citas, planes, facturas).
 */
@Service
public class PatientService {

  private final PatientRepository patientRepository;
  private final SubscriptionService subscriptionService;

  public PatientService(
      PatientRepository patientRepository, SubscriptionService subscriptionService) {
    this.patientRepository = patientRepository;
    this.subscriptionService = subscriptionService;
  }

  @Transactional
  public PatientResponse create(CreatePatientRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    // FASE11-03: el plan limita pacientes activos (150 Esencial, 800 Profesional).
    subscriptionService.checkCapacity(tenantId, LimitKey.MAX_PATIENTS,
        patientRepository.countByTenantIdAndIsActiveTrue(tenantId));
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

  @Transactional(readOnly = true)
  public Page<PatientResponse> search(String query, Pageable pageable) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Page<Patient> page;
    if (query == null || query.trim().isEmpty()) {
      page = patientRepository.findAllByTenantIdAndIsActiveTrue(tenantId, pageable);
    } else {
      page = patientRepository.search(tenantId, query.trim(), pageable);
    }

    return page.map(PatientResponse::fromEntity);
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

  Patient findActiveEntity(UUID id) {
    return findActiveOrThrow(id);
  }

  private Patient findActiveOrThrow(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    // Filtro por tenantId explícito además del automático @TenantId
    // (defensa en profundidad por tenant).
    return patientRepository.findByIdAndTenantId(id, tenantId)
        .filter(Patient::isActive)
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));
  }
}

