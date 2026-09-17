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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.security.access.prepost.PreAuthorize;
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
 * CRUD de pacientes con paginación, búsqueda, control de acceso por roles (@PreAuthorize) y manejo centralizado de errores.
 *
 * <p>Aislamiento estricto por tenant y autorización granular por rol:
 * <ul>
 *   <li>Historia clínica (escritura): reservada a PROPIETARIO, ODONTOLOGO, ESPECIALISTA_EXTERNO.</li>
 *   <li>Historia clínica (lectura): confidencial para personal asistencial (PROPIETARIO, ODONTOLOGO, ESPECIALISTA_EXTERNO, AUXILIAR).</li>
 *   <li>Odontograma (escritura): reservado a PROPIETARIO, ODONTOLOGO, ESPECIALISTA_EXTERNO.</li>
 *   <li>Baja lógica de pacientes: acción administrativa de alto nivel reservada a PROPIETARIO.</li>
 *   <li>Gestión demográfica (crear/modificar): PROPIETARIO, RECEPCION, ODONTOLOGO, AUXILIAR.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Pacientes", description = "CRUD, historia clínica, odontograma y archivos protegidos por tenant y rol.")
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
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'AUXILIAR', 'RECEPCION')")
  @Operation(summary = "Subir un archivo del paciente (solo metadatos en BD, binario en S3)")
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
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'AUXILIAR', 'RECEPCION')")
  @Operation(summary = "Listar archivos del paciente")
  public ResponseEntity<List<PatientFileResponse>> listFiles(@PathVariable UUID id) {
    return ResponseEntity.ok(patientFileService.listByPatient(id));
  }

  @GetMapping("/{id}/files/{fileId}/download-url")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'AUXILIAR', 'RECEPCION')")
  @Operation(summary = "Obtener URL de descarga de un archivo")
  public ResponseEntity<PatientFileDownloadResponse> getFileDownloadUrl(
      @PathVariable UUID id, @PathVariable UUID fileId) {
    return ResponseEntity.ok(patientFileService.getDownloadUrl(id, fileId));
  }

  @PostMapping("/{id}/clinical-records")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO')")
  @Operation(summary = "Agregar una entrada a la historia clínica")
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
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'AUXILIAR')")
  @Operation(summary = "Listar el historial clínico (más reciente primero)")
  public ResponseEntity<List<ClinicalRecordResponse>> listClinicalRecords(@PathVariable UUID id) {
    return ResponseEntity.ok(clinicalRecordService.listByPatient(id));
  }

  @PostMapping("/{id}/odontogram")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO')")
  @Operation(summary = "Registrar una entrada de odontograma (nunca sobrescribe)")
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
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'ESPECIALISTA_EXTERNO', 'AUXILIAR', 'RECEPCION')")
  @Operation(summary = "Ver el odontograma agrupado por pieza y por tipo")
  public ResponseEntity<OdontogramResponse> getOdontogram(@PathVariable UUID id) {
    return ResponseEntity.ok(odontogramService.getOdontogram(id));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'RECEPCION', 'ODONTOLOGO', 'AUXILIAR')")
  @Operation(summary = "Crear un paciente en el tenant autenticado")
  public ResponseEntity<PatientResponse> create(@Valid @RequestBody CreatePatientRequest request) {
    PatientResponse created = patientService.create(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(created.getId())
        .toUri();
    return ResponseEntity.created(location).body(created);
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'RECEPCION', 'ODONTOLOGO', 'AUXILIAR', 'ESPECIALISTA_EXTERNO')")
  @Operation(summary = "Listar pacientes con paginación y búsqueda opcional")
  public ResponseEntity<Page<PatientResponse>> list(
      @RequestParam(required = false) String query,
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(patientService.search(query, pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'RECEPCION', 'ODONTOLOGO', 'AUXILIAR', 'ESPECIALISTA_EXTERNO')")
  @Operation(summary = "Obtener un paciente por ID (404 si es de otro tenant)")
  public ResponseEntity<PatientResponse> getById(@PathVariable UUID id) {
    return ResponseEntity.ok(patientService.getById(id));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'RECEPCION', 'ODONTOLOGO', 'AUXILIAR')")
  @Operation(summary = "Actualizar parcialmente un paciente")
  public ResponseEntity<PatientResponse> patch(
      @PathVariable UUID id, @Valid @RequestBody UpdatePatientRequest request) {
    return ResponseEntity.ok(patientService.patch(id, request));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(summary = "Dar de baja un paciente (baja lógica, no borra la fila)")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    // Baja lógica: marca is_active = false, no borra la fila (FASE2-02).
    patientService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
