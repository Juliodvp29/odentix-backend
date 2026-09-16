package com.julio.odentix.odentix_backend.patient.service;

import com.julio.odentix.odentix_backend.patient.dto.ClinicalRecordResponse;
import com.julio.odentix.odentix_backend.patient.dto.CreateClinicalRecordRequest;
import com.julio.odentix.odentix_backend.patient.entity.ClinicalRecord;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.ClinicalRecordRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClinicalRecordService {

  private final ClinicalRecordRepository clinicalRecordRepository;
  private final PatientService patientService;

  public ClinicalRecordService(
      ClinicalRecordRepository clinicalRecordRepository, PatientService patientService) {
    this.clinicalRecordRepository = clinicalRecordRepository;
    this.patientService = patientService;
  }

  @Transactional
  public ClinicalRecordResponse addEntry(UUID patientId, CreateClinicalRecordRequest request) {
    Patient patient = patientService.findActiveEntity(patientId);
    ClinicalRecord record = new ClinicalRecord(TenantContext.getRequiredTenantId(), patient);
    record.setChiefComplaint(request.getChiefComplaint());
    record.setAnamnesis(request.getAnamnesis());
    record.setDiagnosis(request.getDiagnosis());
    record.setEvolution(request.getEvolution());
    record.setProfessionalId(request.getProfessionalId());
    if (request.getRecordedAt() != null) {
      record.setRecordedAt(request.getRecordedAt());
    }
    return ClinicalRecordResponse.fromEntity(clinicalRecordRepository.save(record));
  }

  @Transactional(readOnly = true)
  public List<ClinicalRecordResponse> listByPatient(UUID patientId) {
    patientService.findActiveEntity(patientId);
    return clinicalRecordRepository.findAllByTenantIdAndPatientIdOrderByRecordedAtDesc(
            TenantContext.getRequiredTenantId(), patientId).stream()
        .map(ClinicalRecordResponse::fromEntity)
        .toList();
  }
}
