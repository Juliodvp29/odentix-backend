package com.julio.odentix.odentix_backend.patient.service;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.patient.dto.PatientFileResponse;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.entity.PatientFile;
import com.julio.odentix.odentix_backend.patient.repository.PatientFileRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.storage.FileStorageService;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Servicio para gestión y subida de archivos de pacientes (FASE2-09).
 *
 * <p>Aplica aislamiento multi-tenant en el storage key:
 * {@code tenants/{tenantId}/patients/{patientId}/{uuid}-{fileName}}
 *
 * <p>Criterio de no orfandad: si la persistencia de metadatos en base de datos falla,
 * se ejecuta una acción de compensación eliminando el archivo subido a S3.
 */
@Service
public class PatientFileService {

  private static final Logger log = LoggerFactory.getLogger(PatientFileService.class);

  private final PatientFileRepository patientFileRepository;
  private final PatientService patientService;
  private final FileStorageService fileStorageService;
  private final TransactionTemplate transactionTemplate;

  public PatientFileService(
      PatientFileRepository patientFileRepository,
      PatientService patientService,
      FileStorageService fileStorageService,
      TransactionTemplate transactionTemplate) {
    this.patientFileRepository = patientFileRepository;
    this.patientService = patientService;
    this.fileStorageService = fileStorageService;
    this.transactionTemplate = transactionTemplate;
  }

  public PatientFileResponse uploadFile(
      UUID patientId, MultipartFile file, AuthenticatedUser currentUser) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("El archivo no puede estar vacío");
    }

    // 1. Validar que el paciente exista, esté activo y pertenezca al tenant actual (404 si no)
    Patient patient = patientService.findActiveEntity(patientId);
    UUID tenantId = TenantContext.getRequiredTenantId();

    String originalFilename = file.getOriginalFilename();
    String safeFilename = sanitizeFilename(originalFilename);
    String fileUuid = UUID.randomUUID().toString();
    String storageKey = String.format("tenants/%s/patients/%s/%s-%s", tenantId, patientId, fileUuid, safeFilename);

    // 2. Subir a S3 primero (si falla aquí, no se toca la base de datos)
    try {
      fileStorageService.upload(
          storageKey, file.getInputStream(), file.getSize(), file.getContentType());
    } catch (IOException e) {
      throw new IllegalArgumentException("No se pudo leer el archivo: " + e.getMessage(), e);
    }

    // 3. Persistir metadatos en base de datos mediante transacción
    try {
      PatientFile patientFile = transactionTemplate.execute(status -> {
        PatientFile entity = new PatientFile(
            tenantId,
            patient,
            storageKey,
            safeFilename,
            file.getContentType(),
            file.getSize(),
            currentUser != null ? currentUser.getUserId() : null);
        return patientFileRepository.save(entity);
      });

      return PatientFileResponse.fromEntity(patientFile);
    } catch (RuntimeException ex) {
      log.error("Fallo al persistir metadatos de archivo para paciente {}. Compensando en S3 key={}: {}",
          patientId, storageKey, ex.getMessage());
      // Compensación: eliminar el archivo de S3 para evitar huérfanos
      fileStorageService.delete(storageKey);
      throw ex;
    }
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public java.util.List<PatientFileResponse> listByPatient(UUID patientId) {
    // 1. Validar que el paciente exista, esté activo y pertenezca al tenant actual (404 si no)
    patientService.findActiveEntity(patientId);

    return patientFileRepository.findByPatientIdOrderByCreatedAtDesc(patientId).stream()
        .map(PatientFileResponse::fromEntity)
        .toList();
  }

  @org.springframework.transaction.annotation.Transactional(readOnly = true)
  public com.julio.odentix.odentix_backend.patient.dto.PatientFileDownloadResponse getDownloadUrl(
      UUID patientId, UUID fileId) {
    // 1. Validar que el paciente exista, esté activo y pertenezca al tenant actual (404 si no)
    patientService.findActiveEntity(patientId);
    UUID tenantId = TenantContext.getRequiredTenantId();

    // 2. Validar que el archivo exista en el tenant y pertenezca exactamente a este paciente
    PatientFile file = patientFileRepository.findById(fileId)
        .filter(f -> f.getPatient().getId().equals(patientId) && f.getTenantId().equals(tenantId))
        .orElseThrow(() -> new com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException("Archivo no encontrado"));

    // 3. Generar URL prefirmada válida por 15 minutos
    java.time.Duration duration = java.time.Duration.ofMinutes(15);
    String presignedUrl = fileStorageService.generatePresignedUrl(file.getStorageKey(), duration);
    java.time.Instant expiresAt = java.time.Instant.now().plus(duration);

    return new com.julio.odentix.odentix_backend.patient.dto.PatientFileDownloadResponse(
        file.getId(), file.getFileName(), presignedUrl, expiresAt);
  }

  private String sanitizeFilename(String filename) {
    if (filename == null || filename.isBlank()) {
      return "archivo";
    }
    String name = filename.replace("\\", "/");
    int lastSlash = name.lastIndexOf('/');
    if (lastSlash >= 0) {
      name = name.substring(lastSlash + 1);
    }
    return name.replaceAll("[^a-zA-Z0-9._-]", "_");
  }
}
