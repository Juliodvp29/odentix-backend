package com.julio.odentix.odentix_backend.crm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadActivityRequest;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadStatusRequest;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivityType;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para los endpoints REST del CRM de leads (FASE5-02)
 * contra PostgreSQL 16 real vía Testcontainers.
 *
 * <p>Cubre:
 * <ul>
 *   <li>Alta, consulta por ID, listado con filtros y actualización de prospectos.</li>
 *   <li>Transición flexible de estados en el pipeline comercial (avances y retrocesos).</li>
 *   <li>Registro de actividades y actualización reactiva de {@code lastContactAt}.</li>
 *   <li>Historial cronológico de interacciones.</li>
 *   <li>Aislamiento multi-tenant estricto (§5 de AGENTS.md).</li>
 *   <li>Autorización y control de acceso por rol (§7 de AGENTS.md).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class LeadIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenRecepcionA;
  private String tokenOdontologoA;
  private String tokenPropietarioA;
  private String tokenRecepcionB;
  private User userRecepcionA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica CRM Alfa", "901999888-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica CRM Beta", "902999888-2"));

    userRecepcionA = userService.createUser(
        tenantA.getId(), "recepcion.crm@alfa.test", "ClaveSegura123!", "María Recepción", UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(userRecepcionA);

    User odontologoA = userService.createUser(
        tenantA.getId(), "odontologo.crm@alfa.test", "ClaveSegura123!", "Dr. Sergio", UserRole.odontologo);
    tokenOdontologoA = jwtService.generateToken(odontologoA);

    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario.crm@alfa.test", "ClaveSegura123!", "Dr. Roberto Propietario", UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);

    User userRecepcionB = userService.createUser(
        tenantB.getId(), "recepcion.crm@beta.test", "ClaveSegura123!", "Luisa Beta", UserRole.recepcion);
    tokenRecepcionB = jwtService.generateToken(userRecepcionB);
  }

  @Test
  void crearLeadManualExitoso() throws Exception {
    CreateLeadRequest request = new CreateLeadRequest("Carolina Montoya", "+573001112233", "carolina@example.com", "instagram");
    request.setCampaign("diseno_sonrisa_abril");
    request.setProcedureOfInterest("Carillas cerámicas");
    request.setEstimatedValueCop(new BigDecimal("4500000.00"));
    request.setAssignedToId(userRecepcionA.getId());

    mockMvc.perform(post("/api/v1/leads")
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", Matchers.containsString("/api/v1/leads/")))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.fullName").value("Carolina Montoya"))
        .andExpect(jsonPath("$.email").value("carolina@example.com"))
        .andExpect(jsonPath("$.source").value("instagram"))
        .andExpect(jsonPath("$.campaign").value("diseno_sonrisa_abril"))
        .andExpect(jsonPath("$.procedureOfInterest").value("Carillas cerámicas"))
        .andExpect(jsonPath("$.estimatedValueCop").value(4500000.00))
        .andExpect(jsonPath("$.status").value("nuevo"))
        .andExpect(jsonPath("$.assignedToId").value(userRecepcionA.getId().toString()))
        .andExpect(jsonPath("$.assignedToName").value("María Recepción"));
  }

  @Test
  void consultarLeadPorIdExitoso() throws Exception {
    UUID leadId = createLead("David Castaño", "+573105556677", "david@example.com", "google_ads");

    mockMvc.perform(get("/api/v1/leads/{id}", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(leadId.toString()))
        .andExpect(jsonPath("$.fullName").value("David Castaño"))
        .andExpect(jsonPath("$.source").value("google_ads"))
        .andExpect(jsonPath("$.status").value("nuevo"));
  }

  @Test
  void listarLeadsConFiltros() throws Exception {
    UUID lead1Id = createLead("Prospecto Uno", "+573001111111", "uno@test.com", "meta_ads");
    UUID lead2Id = createLead("Prospecto Dos", "+573002222222", "dos@test.com", "meta_ads");
    UUID lead3Id = createLead("Prospecto Tres", "+573003333333", "tres@test.com", "referido");

    // Cambiar estado de lead2 a calificado
    mockMvc.perform(patch("/api/v1/leads/{id}/status", lead2Id)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadStatusRequest(LeadStatus.calificado))))
        .andExpect(status().isOk());

    // 1. Filtrar por estado = calificado -> solo debe retornar lead2
    mockMvc.perform(get("/api/v1/leads")
            .param("status", "calificado")
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(lead2Id.toString()));

    // 2. Filtrar por source = referido -> solo debe retornar lead3
    mockMvc.perform(get("/api/v1/leads")
            .param("source", "referido")
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(lead3Id.toString()));
  }

  @Test
  void actualizarDatosDeLeadExitoso() throws Exception {
    UUID leadId = createLead("Laura Restrepo", "+573151234567", "laura@example.com", "organico");

    UpdateLeadRequest updateReq = new UpdateLeadRequest("Laura Sofía Restrepo", "+573159999999", "laura.restrepo@example.com", "instagram");
    updateReq.setProcedureOfInterest("Blanqueamiento dental");
    updateReq.setEstimatedValueCop(new BigDecimal("600000.00"));

    mockMvc.perform(put("/api/v1/leads/{id}", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateReq)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(leadId.toString()))
        .andExpect(jsonPath("$.fullName").value("Laura Sofía Restrepo"))
        .andExpect(jsonPath("$.phone").value("+573159999999"))
        .andExpect(jsonPath("$.source").value("instagram"))
        .andExpect(jsonPath("$.procedureOfInterest").value("Blanqueamiento dental"))
        .andExpect(jsonPath("$.estimatedValueCop").value(600000.00));
  }

  @Test
  void cambiarEstadoDeLeadLibrementeEnPipeline() throws Exception {
    UUID leadId = createLead("Andrés Mejía", "+573201112244", "andres@example.com", "meta_ads");

    // 1. nuevo -> contactado
    mockMvc.perform(patch("/api/v1/leads/{id}/status", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadStatusRequest(LeadStatus.contactado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("contactado"));

    // 2. contactado -> cita_propuesta
    mockMvc.perform(patch("/api/v1/leads/{id}/status", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadStatusRequest(LeadStatus.cita_propuesta))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("cita_propuesta"));

    // 3. Flexibilidad del pipeline: retroceso cita_propuesta -> contactado con nota
    UpdateLeadStatusRequest retroceso = new UpdateLeadStatusRequest(
        LeadStatus.contactado, "El paciente indicó que viajará y pidió volver a contactarlo el próximo mes.");

    mockMvc.perform(patch("/api/v1/leads/{id}/status", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(retroceso)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("contactado"));

    // 4. Verificar que se creó automáticamente la nota explicativa
    mockMvc.perform(get("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].activityType").value("nota"))
        .andExpect(jsonPath("$[0].notes", Matchers.containsString("volver a contactarlo el próximo mes")));
  }

  @Test
  void registrarActividadDeContactoYActualizarLastContactAt() throws Exception {
    UUID leadId = createLead("Paola Andrea Ruiz", "+573118889900", "paola@example.com", "whatsapp");

    // Inicialmente lastContactAt es null
    mockMvc.perform(get("/api/v1/leads/{id}", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastContactAt").doesNotExist());

    // Registrar interacción de WhatsApp
    CreateLeadActivityRequest activityReq = new CreateLeadActivityRequest(
        LeadActivityType.whatsapp, "Se envió información detallada y precios de implantes dentales.");

    mockMvc.perform(post("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(activityReq)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", Matchers.containsString("/api/v1/leads/" + leadId + "/")))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.leadId").value(leadId.toString()))
        .andExpect(jsonPath("$.activityType").value("whatsapp"))
        .andExpect(jsonPath("$.notes").value("Se envió información detallada y precios de implantes dentales."))
        .andExpect(jsonPath("$.userId").value(userRecepcionA.getId().toString()))
        .andExpect(jsonPath("$.userName").value("María Recepción"));

    // Ahora lastContactAt en el lead debe estar presente
    mockMvc.perform(get("/api/v1/leads/{id}", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastContactAt").isNotEmpty());
  }

  @Test
  void listarHistorialDeActividadesOrdenadas() throws Exception {
    UUID leadId = createLead("Jorge Ramírez", "+573174443322", "jorge@example.com", "google_ads");

    // Registrar llamada primero
    CreateLeadActivityRequest act1 = new CreateLeadActivityRequest(LeadActivityType.llamada, "Intento de llamada, sin respuesta.");
    mockMvc.perform(post("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(act1)))
        .andExpect(status().isCreated());

    // Registrar mensaje de WhatsApp después
    CreateLeadActivityRequest act2 = new CreateLeadActivityRequest(LeadActivityType.whatsapp, "Mensaje de WhatsApp respondido por el paciente.");
    mockMvc.perform(post("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(act2)))
        .andExpect(status().isCreated());

    // Consultar historial
    mockMvc.perform(get("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].activityType").value("whatsapp"))
        .andExpect(jsonPath("$[1].activityType").value("llamada"));
  }

  @Test
  void aislamientoCrossTenantEstricto() throws Exception {
    UUID leadAId = createLead("Lead Confidencial Alfa", "+573007778899", "confidencial@alfa.test", "meta_ads");

    // Desde Tenant B no se puede ver ni modificar el lead de Tenant A
    mockMvc.perform(get("/api/v1/leads/{id}", leadAId)
            .header("Authorization", "Bearer " + tokenRecepcionB))
        .andExpect(status().isNotFound());

    mockMvc.perform(put("/api/v1/leads/{id}", leadAId)
            .header("Authorization", "Bearer " + tokenRecepcionB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadRequest("Intruso", "+573000000000", "intruso@beta.test", "hack"))))
        .andExpect(status().isNotFound());

    mockMvc.perform(patch("/api/v1/leads/{id}/status", leadAId)
            .header("Authorization", "Bearer " + tokenRecepcionB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadStatusRequest(LeadStatus.perdido))))
        .andExpect(status().isNotFound());

    mockMvc.perform(post("/api/v1/leads/{id}/activities", leadAId)
            .header("Authorization", "Bearer " + tokenRecepcionB)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateLeadActivityRequest(LeadActivityType.nota, "Nota ilegal"))))
        .andExpect(status().isNotFound());

    mockMvc.perform(get("/api/v1/leads/{id}/activities", leadAId)
            .header("Authorization", "Bearer " + tokenRecepcionB))
        .andExpect(status().isNotFound());

    // Listado en Tenant B no incluye el lead de Tenant A
    mockMvc.perform(get("/api/v1/leads")
            .header("Authorization", "Bearer " + tokenRecepcionB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void autorizacionPorRol() throws Exception {
    UUID leadId = createLead("Paciente Prospecto", "+573004445566", "prospecto@alfa.test", "referido");

    // 1. Odontólogo puede consultar y registrar actividades
    mockMvc.perform(get("/api/v1/leads/{id}", leadId)
            .header("Authorization", "Bearer " + tokenOdontologoA))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/leads/{id}/activities", leadId)
            .header("Authorization", "Bearer " + tokenOdontologoA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new CreateLeadActivityRequest(LeadActivityType.nota, "Nota de evaluación clínica"))))
        .andExpect(status().isCreated());

    // 2. Propietario puede actualizar estado
    mockMvc.perform(patch("/api/v1/leads/{id}/status", leadId)
            .header("Authorization", "Bearer " + tokenPropietarioA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateLeadStatusRequest(LeadStatus.calificado))))
        .andExpect(status().isOk());

    // 3. Petición sin token recibe 401 Unauthorized
    mockMvc.perform(get("/api/v1/leads/{id}", leadId))
        .andExpect(status().isUnauthorized());
  }

  private UUID createLead(String fullName, String phone, String email, String source) throws Exception {
    CreateLeadRequest req = new CreateLeadRequest(fullName, phone, email, source);
    MvcResult result = mockMvc.perform(post("/api/v1/leads")
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(req)))
        .andExpect(status().isCreated())
        .andReturn();

    String json = result.getResponse().getContentAsString();
    JsonNode node = objectMapper.readTree(json);
    return UUID.fromString(node.get("id").asText());
  }
}
