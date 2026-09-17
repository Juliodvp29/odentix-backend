package com.julio.odentix.odentix_backend.patient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.dto.CreateClinicalRecordRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreateOdontogramEntryRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración para la autorización basada en roles (@PreAuthorize) en PatientController.
 */
@AutoConfigureMockMvc
class PatientRoleAuthorizationIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PatientRepository patientRepository;

  private Tenant tenant;
  private String tokenPropietario;
  private String tokenOdontologo;
  private String tokenRecepcion;
  private String tokenEspecialistaExterno;
  private Patient patient;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenant = tenantRepository.save(new Tenant("Clínica Roles Paciente", "901444555-1"));

    User userPropietario = userService.createUser(
        tenant.getId(), "propietario@roles.com", "PassSegura123!", "Dra. Propietaria", UserRole.propietario);
    tokenPropietario = jwtService.generateToken(userPropietario);

    User userOdontologo = userService.createUser(
        tenant.getId(), "odontologo@roles.com", "PassSegura123!", "Dr. Odontólogo", UserRole.odontologo);
    tokenOdontologo = jwtService.generateToken(userOdontologo);

    User userRecepcion = userService.createUser(
        tenant.getId(), "recepcion@roles.com", "PassSegura123!", "Recepcionista", UserRole.recepcion);
    tokenRecepcion = jwtService.generateToken(userRecepcion);

    User userEspecialista = userService.createUser(
        tenant.getId(), "especialista@roles.com", "PassSegura123!", "Dr. Especialista", UserRole.especialista_externo);
    tokenEspecialistaExterno = jwtService.generateToken(userEspecialista);

    TenantContext.setTenantId(tenant.getId());
    patient = new Patient(tenant.getId(), "Carlos", "Restrepo");
    patient.setDocumentNumber("DOC-ROLE-" + UUID.randomUUID());
    patient = patientRepository.save(patient);
  }

  @Test
  @DisplayName("Recepcionista puede registrar y buscar pacientes")
  void recepcionistaPuedeCrearYBuscarPacientes() throws Exception {
    CreatePatientRequest request = new CreatePatientRequest();
    request.setFirstName("Ana");
    request.setLastName("Gómez");
    request.setDocumentNumber("CC-" + UUID.randomUUID());

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.firstName").value("Ana"));

    mockMvc.perform(get("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Especialista externo NO puede crear pacientes directamente (403 Forbidden)")
  void especialistaExternoNoPuedeCrearPacientes() throws Exception {
    CreatePatientRequest request = new CreatePatientRequest();
    request.setFirstName("Pedro");
    request.setLastName("López");
    request.setDocumentNumber("CC-" + UUID.randomUUID());

    mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenEspecialistaExterno)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Recepcionista NO puede dar de baja a un paciente (403 Forbidden)")
  void recepcionistaNoPuedeDarDeBajaPaciente() throws Exception {
    mockMvc.perform(delete("/api/v1/patients/" + patient.getId())
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Propietario sí puede dar de baja a un paciente (204 No Content)")
  void propietarioPuedeDarDeBajaPaciente() throws Exception {
    mockMvc.perform(delete("/api/v1/patients/" + patient.getId())
            .header("Authorization", "Bearer " + tokenPropietario))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("Recepcionista NO puede escribir en historia clínica (403 Forbidden)")
  void recepcionistaNoPuedeCrearEntradaHistoriaClinica() throws Exception {
    CreateClinicalRecordRequest request = new CreateClinicalRecordRequest();
    request.setChiefComplaint("Dolor de muela");
    request.setEvolution("Se observa caries en pieza 16");

    mockMvc.perform(post("/api/v1/patients/" + patient.getId() + "/clinical-records")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Odontólogo sí puede escribir en historia clínica (201 Created)")
  void odontologoPuedeCrearEntradaHistoriaClinica() throws Exception {
    CreateClinicalRecordRequest request = new CreateClinicalRecordRequest();
    request.setChiefComplaint("Consulta general");
    request.setEvolution("Paciente en buen estado general");

    mockMvc.perform(post("/api/v1/patients/" + patient.getId() + "/clinical-records")
            .header("Authorization", "Bearer " + tokenOdontologo)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.chiefComplaint").value("Consulta general"));
  }

  @Test
  @DisplayName("Recepcionista NO puede leer la historia clínica (403 Forbidden)")
  void recepcionistaNoPuedeLeerHistoriaClinica() throws Exception {
    mockMvc.perform(get("/api/v1/patients/" + patient.getId() + "/clinical-records")
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Odontólogo sí puede leer la historia clínica (200 OK)")
  void odontologoPuedeLeerHistoriaClinica() throws Exception {
    mockMvc.perform(get("/api/v1/patients/" + patient.getId() + "/clinical-records")
            .header("Authorization", "Bearer " + tokenOdontologo))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Recepcionista NO puede registrar hallazgos en el odontograma (403 Forbidden)")
  void recepcionistaNoPuedeRegistrarOdontograma() throws Exception {
    CreateOdontogramEntryRequest request = new CreateOdontogramEntryRequest();
    request.setToothNumber(16);
    request.setEntryType(OdontogramEntryType.diagnostico);
    request.setCondition("Caries oclusal");

    mockMvc.perform(post("/api/v1/patients/" + patient.getId() + "/odontogram")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Acceso denegado"));
  }

  @Test
  @DisplayName("Odontólogo sí puede registrar hallazgos en el odontograma (201 Created)")
  void odontologoPuedeRegistrarOdontograma() throws Exception {
    CreateOdontogramEntryRequest request = new CreateOdontogramEntryRequest();
    request.setToothNumber(16);
    request.setEntryType(OdontogramEntryType.diagnostico);
    request.setCondition("Caries oclusal");

    mockMvc.perform(post("/api/v1/patients/" + patient.getId() + "/odontogram")
            .header("Authorization", "Bearer " + tokenOdontologo)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.toothNumber").value(16));
  }

  @Test
  @DisplayName("Recepcionista sí puede ver el odontograma gráfico (200 OK)")
  void recepcionistaPuedeVerOdontograma() throws Exception {
    mockMvc.perform(get("/api/v1/patients/" + patient.getId() + "/odontogram")
            .header("Authorization", "Bearer " + tokenRecepcion))
        .andExpect(status().isOk());
  }
}
