package com.julio.odentix.odentix_backend.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.entity.PatientFile;
import com.julio.odentix.odentix_backend.patient.repository.PatientFileRepository;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.storage.FileStorageService;
import com.julio.odentix.odentix_backend.shared.storage.exception.StorageException;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración del endpoint POST /api/v1/patients/{id}/files (FASE2-09).
 *
 * <p>Cubre el DoD:
 * 1. Subida exitosa multipart con metadatos en DB.
 * 2. Criterio de no orfandad: si la base de datos falla, compensa eliminando en S3.
 * 3. Si S3 falla de entrada, no se crea registro en base de datos.
 * 4. Aislamiento cross-tenant: intento sobre paciente de otro tenant devuelve 404.
 * 5. Rechazo de archivos vacíos con 400.
 */
@AutoConfigureMockMvc
class PatientFileUploadIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PatientRepository patientRepository;

  @MockitoSpyBean
  private PatientFileRepository patientFileRepository;

  @MockitoBean
  private FileStorageService fileStorageService;

  private Tenant tenantA;
  private Tenant tenantB;
  private User userA;
  private String tokenA;
  private String tokenB;
  private Patient patientA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Archivos Test A", "902111222-1"));
    userA = userService.createUser(
        tenantA.getId(), "admin@archivos-a.com", "ClaveSegura123!", "Admin Archivos A", UserRole.propietario);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Archivos Test B", "902333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "admin@archivos-b.com", "ClaveSegura456!", "Admin Archivos B", UserRole.propietario);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    patientA = new Patient(tenantA.getId(), "Laura", "Zapata");
    patientA.setDocumentNumber("DOC-FILE-" + UUID.randomUUID());
    patientA = patientRepository.save(patientA);

    when(fileStorageService.upload(anyString(), any(InputStream.class), anyLong(), anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  @DisplayName("Subir archivo exitosamente guarda el objeto en storage y los metadatos en BD")
  void shouldUploadFileSuccessfully() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "radiografia_panoramica.png",
        "image/png",
        "datos-binarios-simulados".getBytes());

    mockMvc.perform(multipart("/api/v1/patients/{id}/files", patientA.getId())
            .file(file)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.fileName").value("radiografia_panoramica.png"))
        .andExpect(jsonPath("$.contentType").value("image/png"))
        .andExpect(jsonPath("$.sizeBytes").value(file.getSize()))
        .andExpect(jsonPath("$.uploadedBy").value(userA.getId().toString()))
        .andExpect(jsonPath("$.createdAt").isNotEmpty());

    // Verificar que se llamó a storage con la convención tenants/{tenantId}/patients/{patientId}/...
    ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
    verify(fileStorageService).upload(keyCaptor.capture(), any(InputStream.class), eq(file.getSize()), eq("image/png"));

    String storageKey = keyCaptor.getValue();
    assertThat(storageKey)
        .startsWith("tenants/" + tenantA.getId() + "/patients/" + patientA.getId() + "/")
        .endsWith("-radiografia_panoramica.png");

    // Verificar en BD
    TenantContext.setTenantId(tenantA.getId());
    List<PatientFile> files = patientFileRepository.findByPatientIdOrderByCreatedAtDesc(patientA.getId());
    assertThat(files).hasSize(1);
    assertThat(files.get(0).getStorageKey()).isEqualTo(storageKey);
  }

  @Test
  @DisplayName("Compensación: si la base de datos falla tras subir a storage, elimina el archivo de S3 para evitar huérfanos")
  void shouldCompensateAndDeleteFromStorageIfDatabaseSaveFails() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "consentimiento.pdf",
        "application/pdf",
        "contenido-pdf".getBytes());

    // Forzar fallo en persistencia de BD
    doThrow(new RuntimeException("Simulated database failure during metadata save"))
        .when(patientFileRepository).save(any(PatientFile.class));

    mockMvc.perform(multipart("/api/v1/patients/{id}/files", patientA.getId())
            .file(file)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isInternalServerError());

    // Verificar que se ejecutó la llamada de compensación 'delete' en storage con la misma key
    ArgumentCaptor<String> deletedKeyCaptor = ArgumentCaptor.forClass(String.class);
    verify(fileStorageService).delete(deletedKeyCaptor.capture());

    assertThat(deletedKeyCaptor.getValue())
        .startsWith("tenants/" + tenantA.getId() + "/patients/" + patientA.getId() + "/")
        .endsWith("-consentimiento.pdf");
  }

  @Test
  @DisplayName("Si el almacenamiento falla de entrada, responde 500 y no crea registro en BD")
  void shouldNotCreateDatabaseRecordIfStorageFails() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "foto_diagnostico.jpg",
        "image/jpeg",
        "foto-bytes".getBytes());

    when(fileStorageService.upload(anyString(), any(InputStream.class), anyLong(), anyString()))
        .thenThrow(new StorageException("S3 bucket unavailable"));

    mockMvc.perform(multipart("/api/v1/patients/{id}/files", patientA.getId())
            .file(file)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Error en el almacenamiento de archivos"));

    TenantContext.setTenantId(tenantA.getId());
    List<PatientFile> files = patientFileRepository.findByPatientIdOrderByCreatedAtDesc(patientA.getId());
    assertThat(files).isEmpty();
  }

  @Test
  @DisplayName("Aislamiento cross-tenant: usuario de Tenant B recibe 404 al intentar subir archivo a paciente de Tenant A")
  void shouldRejectCrossTenantUploadWith404() throws Exception {
    MockMultipartFile file = new MockMultipartFile(
        "file",
        "archivo_ajeno.pdf",
        "application/pdf",
        "contenido".getBytes());

    mockMvc.perform(multipart("/api/v1/patients/{id}/files", patientA.getId())
            .file(file)
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));

    TenantContext.setTenantId(tenantA.getId());
    List<PatientFile> files = patientFileRepository.findByPatientIdOrderByCreatedAtDesc(patientA.getId());
    assertThat(files).isEmpty();
  }

  @Test
  @DisplayName("Rechaza archivo vacío con 400 Bad Request")
  void shouldRejectEmptyFileWith400() throws Exception {
    MockMultipartFile emptyFile = new MockMultipartFile(
        "file",
        "vacio.txt",
        "text/plain",
        new byte[0]);

    mockMvc.perform(multipart("/api/v1/patients/{id}/files", patientA.getId())
            .file(emptyFile)
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("El archivo no puede estar vacío"));
  }

  @Test
  @DisplayName("Lista los archivos asociados a un paciente ordenados por fecha de creación (FASE2-10)")
  void shouldListFilesForPatient() throws Exception {
    TenantContext.setTenantId(tenantA.getId());
    try {
      PatientFile file1 = new PatientFile(
          tenantA.getId(), patientA, "tenants/a/patients/p/1.png", "radiografia1.png", "image/png", 100L, userA.getId());
      patientFileRepository.save(file1);

      PatientFile file2 = new PatientFile(
          tenantA.getId(), patientA, "tenants/a/patients/p/2.pdf", "consentimiento.pdf", "application/pdf", 200L, userA.getId());
      patientFileRepository.save(file2);
    } finally {
      TenantContext.clear();
    }

    mockMvc.perform(get("/api/v1/patients/{id}/files", patientA.getId())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].fileName").isNotEmpty())
        .andExpect(jsonPath("$[1].fileName").isNotEmpty());
  }

  @Test
  @DisplayName("Obtiene URL de descarga prefirmada exitosamente (FASE2-10)")
  void shouldGetDownloadUrlSuccessfully() throws Exception {
    PatientFile file;
    TenantContext.setTenantId(tenantA.getId());
    try {
      file = new PatientFile(
          tenantA.getId(), patientA, "tenants/a/patients/p/rx.png", "rx.png", "image/png", 1500L, userA.getId());
      file = patientFileRepository.save(file);
    } finally {
      TenantContext.clear();
    }

    String mockPresignedUrl = "https://odentix-bucket.s3.us-east-1.amazonaws.com/tenants/a/patients/p/rx.png?signed=true";
    when(fileStorageService.generatePresignedUrl(eq(file.getStorageKey()), any(java.time.Duration.class)))
        .thenReturn(mockPresignedUrl);

    mockMvc.perform(get("/api/v1/patients/{id}/files/{fileId}/download-url", patientA.getId(), file.getId())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.fileId").value(file.getId().toString()))
        .andExpect(jsonPath("$.fileName").value("rx.png"))
        .andExpect(jsonPath("$.downloadUrl").value(mockPresignedUrl))
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());
  }

  @Test
  @DisplayName("Aislamiento cross-tenant: usuario de Tenant B no puede listar archivos de paciente de Tenant A (404)")
  void shouldRejectCrossTenantListWith404() throws Exception {
    mockMvc.perform(get("/api/v1/patients/{id}/files", patientA.getId())
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
  }

  @Test
  @DisplayName("Aislamiento cross-tenant: usuario de Tenant B no puede obtener URL de descarga de archivo de Tenant A (404)")
  void shouldRejectCrossTenantDownloadUrlWith404() throws Exception {
    PatientFile file;
    TenantContext.setTenantId(tenantA.getId());
    try {
      file = new PatientFile(
          tenantA.getId(), patientA, "tenants/a/patients/p/topsecret.pdf", "topsecret.pdf", "application/pdf", 1000L, userA.getId());
      file = patientFileRepository.save(file);
    } finally {
      TenantContext.clear();
    }

    mockMvc.perform(get("/api/v1/patients/{id}/files/{fileId}/download-url", patientA.getId(), file.getId())
            .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Paciente no encontrado"));
  }

  @Test
  @DisplayName("Rechaza obtener URL si el archivo no pertenece al paciente indicado (404)")
  void shouldRejectDownloadUrlIfFileBelongsToAnotherPatient() throws Exception {
    Patient otherPatient;
    PatientFile fileForFirstPatient;
    TenantContext.setTenantId(tenantA.getId());
    try {
      otherPatient = new Patient(tenantA.getId(), "Pedro", "Navaja");
      otherPatient = patientRepository.save(otherPatient);

      fileForFirstPatient = new PatientFile(
          tenantA.getId(), patientA, "tenants/a/patients/p/first.pdf", "first.pdf", "application/pdf", 100L, userA.getId());
      fileForFirstPatient = patientFileRepository.save(fileForFirstPatient);
    } finally {
      TenantContext.clear();
    }

    // Intentar acceder al archivo del paciente A a través de la ruta del paciente B (mismo tenant)
    mockMvc.perform(get("/api/v1/patients/{id}/files/{fileId}/download-url", otherPatient.getId(), fileForFirstPatient.getId())
            .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Archivo no encontrado"));
  }
}
