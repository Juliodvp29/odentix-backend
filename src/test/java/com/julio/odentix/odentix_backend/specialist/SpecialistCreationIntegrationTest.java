package com.julio.odentix.odentix_backend.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para la creación de fichas de especialistas
 * ({@code POST /api/v1/specialists}) contra PostgreSQL real vía
 * Testcontainers.
 *
 * <p>Cubre:
 * <ul>
 *   <li>Crear ficha sobre un profesional externo → 201 con la ficha.</li>
 *   <li>Segunda ficha para el mismo profesional → 409.</li>
 *   <li>Profesional de planta → 400 (validación de externo en aplicación).</li>
 *   <li>Profesional de otro tenant o inexistente → 404.</li>
 *   <li>Autorización por rol: solo PROPIETARIO crea fichas (recepción → 403).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SpecialistCreationIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private ProfessionalRepository professionalRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenPropietarioB;
  private Professional externaSinFichaA;
  private Professional plantaA;
  private Professional externaB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Ficha Alfa", "960111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@ficha-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@ficha-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Ficha Beta", "961333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@ficha-beta.com", "ClaveSegura456!", "Propietario Beta",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      externaSinFichaA = new Professional(tenantA.getId(), "Dra. Externa Sin Ficha");
      externaSinFichaA.setExternal(true);
      externaSinFichaA = professionalRepository.saveAndFlush(externaSinFichaA);

      plantaA = professionalRepository.saveAndFlush(
          new Professional(tenantA.getId(), "Dr. Planta Ficha"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      externaB = new Professional(tenantB.getId(), "Dr. Externo Beta");
      externaB.setExternal(true);
      externaB = professionalRepository.saveAndFlush(externaB);
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private JsonNode crearFicha(String token, String professionalId,
      String feePercentage) throws Exception {
    Map<String, Object> body = Map.of(
        "professionalId", professionalId,
        "feePercentage", new BigDecimal(feePercentage),
        "paymentTerms", "Pago a 30 días");
    MvcResult result = mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal
  // ---------------------------------------------------------------------------

  @Test
  void crearFichaDevuelve201ConLaFicha() throws Exception {
    JsonNode json = crearFicha(
        tokenPropietarioA, externaSinFichaA.getId().toString(), "35.50");

    assertThat(json.get("id").asText()).isNotBlank();
    assertThat(json.get("professionalId").asText())
        .isEqualTo(externaSinFichaA.getId().toString());
    assertThat(json.get("fullName").asText()).isEqualTo("Dra. Externa Sin Ficha");
    assertThat(new BigDecimal(json.get("feePercentage").asText()))
        .isEqualByComparingTo(new BigDecimal("35.50"));
    assertThat(json.get("paymentTerms").asText()).isEqualTo("Pago a 30 días");

    // La ficha creada aparece en el listado de especialistas del tenant.
    MvcResult listado = mockMvc.perform(
            get("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode especialistas = objectMapper.readTree(listado.getResponse().getContentAsString());
    assertThat(especialistas).hasSize(1);
    assertThat(especialistas.get(0).get("id").asText()).isEqualTo(json.get("id").asText());
  }

  @Test
  void segundaFichaParaElMismoProfesionalDevuelve409() throws Exception {
    crearFicha(tokenPropietarioA, externaSinFichaA.getId().toString(), "35.50");

    Map<String, Object> body = Map.of(
        "professionalId", externaSinFichaA.getId().toString(),
        "feePercentage", new BigDecimal("40.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message", Matchers.containsString("ya tiene una ficha")));
  }

  @Test
  void profesionalDePlantaDevuelve400() throws Exception {
    Map<String, Object> body = Map.of(
        "professionalId", plantaA.getId().toString(),
        "feePercentage", new BigDecimal("30.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void feeFueraDeRangoDevuelve400() throws Exception {
    Map<String, Object> body = Map.of(
        "professionalId", externaSinFichaA.getId().toString(),
        "feePercentage", new BigDecimal("150.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant y autorización por rol
  // ---------------------------------------------------------------------------

  @Test
  void profesionalDeOtroTenantDevuelve404() throws Exception {
    // Propietario de B sobre el profesional de A → como si no existiera.
    Map<String, Object> body = Map.of(
        "professionalId", externaSinFichaA.getId().toString(),
        "feePercentage", new BigDecimal("30.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());

    // Y viceversa: A sobre el profesional de B → 404.
    Map<String, Object> inverso = Map.of(
        "professionalId", externaB.getId().toString(),
        "feePercentage", new BigDecimal("30.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(inverso)))
        .andExpect(status().isNotFound());
  }

  @Test
  void profesionalInexistenteDevuelve404() throws Exception {
    Map<String, Object> body = Map.of(
        "professionalId", java.util.UUID.randomUUID().toString(),
        "feePercentage", new BigDecimal("30.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());
  }

  @Test
  void recepcionNoPuedeCrearFichaDevuelve403() throws Exception {
    Map<String, Object> body = Map.of(
        "professionalId", externaSinFichaA.getId().toString(),
        "feePercentage", new BigDecimal("30.00"));
    mockMvc.perform(
            post("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenRecepcionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }
}
