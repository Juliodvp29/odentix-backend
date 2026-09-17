package com.julio.odentix.odentix_backend.patient.repository;

import com.julio.odentix.odentix_backend.patient.entity.PatientFile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para metadatos de archivos de pacientes (FASE2-09).
 * Hereda el filtrado automático de tenant de {@code TenantAwareEntity}.
 */
@Repository
public interface PatientFileRepository extends JpaRepository<PatientFile, UUID> {

  List<PatientFile> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
