package com.julio.odentix.odentix_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de ajustes de la propia clínica (pre-Fase 12): el propietario
 * configura su remitente sin tocar otras clínicas.
 */
@AutoConfigureMockMvc
class TenantSettingsIntegrationTest extends AbstractIntegrationTest {

  @DynamicPropertySource
  static void llaveCifrado(DynamicPropertyRegistry registry) {
    // El cifrado del token exige llave (igual que en prod con DATA_ENCRYPTION_KEY).
    registry.add("odentix.crypto.key",
        () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

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
  private String tokenPropietarioA;
  private String tokenRecepcionA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Ajustes Alfa", "9Q0111222-1"));
    User propietario = userService.createUser(
        tenantA.getId(), "propietario@ajustes-alfa.com", "ClaveSegura123!", "Propietario",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietario);
    User recepcion = userService.createUser(
        tenantA.getId(), "recepcion@ajustes-alfa.com", "ClaveSegura123!", "Recepción",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcion);

    tenantB = tenantRepository.save(new Tenant("Clínica Ajustes Beta", "9Q0133444-2"));
  }

  private JsonNode ajustar(String token, Map<String, Object> body, int esperado) throws Exception {
    MvcResult result = mockMvc.perform(patch("/api/v1/tenant/settings")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().is(esperado))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void propietarioConfiguraYLimpiaRemitente() throws Exception {
    JsonNode guardado = ajustar(tokenPropietarioA,
        Map.of("notificationEmail", "citas@alfa.com", "notificationName", "Alfa"), 200);
    assertThat(guardado.get("notificationEmail").asText()).isEqualTo("citas@alfa.com");
    assertThat(guardado.get("notificationName").asText()).isEqualTo("Alfa");
    assertThat(guardado.get("tenantId").asText()).isEqualTo(tenantA.getId().toString());

    MvcResult visto = mockMvc.perform(get("/api/v1/tenant/settings")
            .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(objectMapper.readTree(visto.getResponse().getContentAsString())
        .get("notificationEmail").asText()).isEqualTo("citas@alfa.com");

    // Vacío limpia y vuelve al global.
    JsonNode limpio = ajustar(tokenPropietarioA, Map.of("notificationEmail", "  "), 200);
    assertThat(limpio.get("notificationEmail").isNull()).isTrue();
  }

  @Test
  void emailInvalidoDevuelve400YNoTocaBeta() throws Exception {
    ajustar(tokenPropietarioA, Map.of("notificationEmail", "no-es-email"), 400);

    TenantContext.setTenantId(tenantB.getId());
    try {
      assertThat(tenantRepository.findById(tenantB.getId()).orElseThrow()
          .getNotificationEmail()).isNull();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void recepcionNoPuedeEditarYSinToken401() throws Exception {
    mockMvc.perform(patch("/api/v1/tenant/settings")
            .header("Authorization", "Bearer " + tokenRecepcionA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("notificationEmail", "x@alfa.com"))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/v1/tenant/settings"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void whatsappPropioSeConfiguraSinExponerToken() throws Exception {
    JsonNode guardado = ajustar(tokenPropietarioA,
        Map.of("whatsappPhoneNumberId", "1234567890", "whatsappToken", "token-secreto"), 200);
    assertThat(guardado.get("whatsappPhoneNumberId").asText()).isEqualTo("1234567890");
    assertThat(guardado.get("whatsappConfigured").asBoolean()).isTrue();
    // El token jamás vuelve en respuestas (write-only).
    assertThat(guardado.toString()).doesNotContain("token-secreto");
    assertThat(guardado.has("whatsappToken")).isFalse();

    // Sin token en el PATCH, el guardado se conserva.
    JsonNode sinCambios = ajustar(tokenPropietarioA,
        Map.of("notificationName", "Alfa"), 200);
    assertThat(sinCambios.get("whatsappConfigured").asBoolean()).isTrue();

    // En BD el token está cifrado, no en claro.
    TenantContext.setTenantId(tenantA.getId());
    try {
      String cifrado = tenantRepository.findById(tenantA.getId()).orElseThrow()
          .getWhatsappTokenCifrado();
      assertThat(cifrado).isNotNull();
      assertThat(cifrado).doesNotContain("token-secreto");
    } finally {
      TenantContext.clear();
    }
  }
}
