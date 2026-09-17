package com.julio.odentix.odentix_backend.patient.controller;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.patient.dto.ClinicalRecordResponse;
import com.julio.odentix.odentix_backend.patient.dto.CreateClinicalRecordRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreateOdontogramEntryRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.dto.OdontogramEntryResponse;
import com.julio.odentix.odentix_backend.patient.dto.OdontogramResponse;
import com.julio.odentix.odentix_backend.patient.dto.PatientFileDownloadResponse;
import com.julio.odentix.odentix_backend.patient.dto.PatientFileResponse;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.dto.UpdatePatientRequest;
import com.julio.odentix.odentix_backend.patient.service.ClinicalRecordService;
import com.julio.odentix.odentix_backend.patient.service.OdontogramService;
import com.julio.odentix.odentix_backend.patient.service.PatientFileService;
import com.julio.odentix.odentix_backend.patient.service.PatientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CRUD de pacientes con paginación, búsqueda y manejo centralizado de errores (FASE2-02 / FASE2-03 / FASE2-09 / FASE2-10).
 *
 * <p>Sin restricción por rol: cualquier usuario autenticado opera sobre los pacientes de su propio tenant.
 * Los errores son gestionados de forma transversal por {@link com.julio.odentix.odentix_backend.shared.exception.GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {

  private final PatientService patientService;
  private final ClinicalRecordService clinicalRecordService;
  private final OdontogramService odontogramService;
  private final PatientFileService patientFileService;

  public PatientController(
      PatientService patientService,
      ClinicalRecordService clinicalRecordService,
      OdontogramService odontogramService,
      PatientFileService patientFileService) {
    this.patientService = patientService;
    this.clinicalRecordService = clinicalRecordService;
    this.odontogramService = odontogramService;
    this.patientFileService = patientFileService;
  }

  @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<PatientFileResponse> uploadFile(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    PatientFileResponse created = patientFileService.uploadFile(id, file, authUser);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{fileId}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}/files")
  public ResponseEntity<List<PatientFileResponse>> listFiles(@PathVariable UUID id) {
    return ResponseEntity.ok(patientFileService.listByPatient(id));
  }

  @GetMapping("/{id}/files/{fileId}/download-url")
  public ResponseEntity<PatientFileDownloadResponse> getFileDownloadUrl(
      @PathVariable UUID id, @PathVariable UUID fileId) {
    return ResponseEntity.ok(patientFileService.getDownloadUrl(id, fileId));
  }

  @PostMapping("/{id}/clinical-records")
  public ResponseEntity<ClinicalRecordResponse> addClinicalRecord(
      @PathVariable UUID id, @Valid @RequestBody CreateClinicalRecordRequest request) {
    ClinicalRecordResponse created = clinicalRecordService.addEntry(id, request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{recordId}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}/clinical-records")
  public ResponseEntity<List<ClinicalRecordResponse>> listClinicalRecords(@PathVariable UUID id) {
    return ResponseEntity.ok(clinicalRecordService.listByPatient(id));
  }

  @PostMapping("/{id}/odontogram")
  public ResponseEntity<OdontogramEntryResponse> addOdontogramEntry(
      @PathVariable UUID id, @Valid @RequestBody CreateOdontogramEntryRequest request) {
    OdontogramEntryResponse created = odontogramService.addEntry(id, request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{entryId}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping("/{id}/odontogram")
  public ResponseEntity<OdontogramResponse> getOdontogram(@PathVariable UUID id) {
    return ResponseEntity.ok(odontogramService.getOdontogram(id));
  }

  @PostMapping
  public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
    PatientResponse created = patientService.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping
  public ResponseEntity<Page<PatientResponse>> list(
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(patientService.search(query, pageable));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(patientService.getById(id));
  }

  @PatchMapping("/{id}")
  public ResponseEntity<PatientResponse> patch(
      @PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
    return ResponseEntity.ok(patientService.patch(id, request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    // Baja lógica: marca is_active = false, no borra la fila (FASE2-02).
    patientService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
