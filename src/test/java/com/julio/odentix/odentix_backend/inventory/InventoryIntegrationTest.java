package com.julio.odentix.odentix_backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para inventario (FASE7-04) contra PostgreSQL
 * real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>CRUD de ítems y registro de movimientos vía API.</li>
 *   <li>{@code GET /api/v1/inventory/critical} devuelve los ítems con
 *       {@code quantity <= min_threshold}.</li>
 *   <li>Un movimiento que dejaría el stock en negativo falla con un 409 claro
 *       (verificado por el CHECK de la base) y el stock queda intacto.</li>
 *   <li>Aislamiento cross-tenant: ítems de otro tenant → 404 (regla §5).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class InventoryIntegrationTest extends AbstractIntegrationTest {

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
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Stock Alfa", "960111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@stock-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Stock Beta", "961333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@stock-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private JsonNode crearItem(String token, String nombre, String unidad, int umbral)
      throws Exception {
    Map<String, Object> body = Map.of(
        "name", nombre,
        "unit", unidad,
        "minThreshold", umbral);
    MvcResult result = mockMvc.perform(
            post("/api/v1/inventory/items")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode moverStock(String token, String itemId, int delta, String motivo)
      throws Exception {
    Map<String, Object> body = Map.of("quantityDelta", delta, "reason", motivo);
    MvcResult result = mockMvc.perform(
            post("/api/v1/inventory/items/{id}/movements", itemId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode verItem(String token, String itemId) throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/inventory/items/{id}", itemId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE7-04)
  // ---------------------------------------------------------------------------

  @Test
  void crearItemNaceEnCeroYDuplicadoDevuelve409() throws Exception {
    JsonNode json = crearItem(tokenA, "Resina", "tubo", 5);

    assertThat(json.get("quantity").asInt()).isZero();
    assertThat(json.get("minThreshold").asInt()).isEqualTo(5);
    assertThat(json.get("name").asText()).isEqualTo("Resina");

    Map<String, Object> duplicado =
        Map.of("name", "Resina", "unit", "tubo", "minThreshold", 5);
    mockMvc.perform(
            post("/api/v1/inventory/items")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicado)))
        .andExpect(status().isConflict());
  }

  @Test
  void entradaYConsumoActualizanStockYCritical() throws Exception {
    String itemId = crearItem(tokenA, "Anestesia", "caja", 5).get("id").asText();

    JsonNode entrada = moverStock(tokenA, itemId, 3, "Compra parcial");
    assertThat(entrada.get("resultingQuantity").asInt()).isEqualTo(3);

    // Con 3 <= umbral 5, el ítem aparece en críticos.
    MvcResult criticos = mockMvc.perform(
            get("/api/v1/inventory/critical")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(criticos.getResponse().getContentAsString()).contains(itemId);

    // Consumo parcial y reposición que lo saca de críticos.
    JsonNode consumo = moverStock(tokenA, itemId, -1, "Consumo");
    assertThat(consumo.get("resultingQuantity").asInt()).isEqualTo(2);
    moverStock(tokenA, itemId, 10, "Reposición");

    assertThat(verItem(tokenA, itemId).get("quantity").asInt()).isEqualTo(12);
    MvcResult criticosDespues = mockMvc.perform(
            get("/api/v1/inventory/critical")
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(criticosDespues.getResponse().getContentAsString()).doesNotContain(itemId);
  }

  @Test
  void actualizarUmbralYCambiarNombre() throws Exception {
    String itemId = crearItem(tokenA, "Guantes", "caja", 20).get("id").asText();

    Map<String, Object> body = Map.of("minThreshold", 30, "name", "Guantes nitrilo");
    MvcResult result = mockMvc.perform(
            patch("/api/v1/inventory/items/{id}", itemId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("minThreshold").asInt()).isEqualTo(30);
    assertThat(json.get("name").asText()).isEqualTo("Guantes nitrilo");
  }

  @Test
  void eliminarSinMovimientos204YConMovimientos409() throws Exception {
    String sinMovimientos = crearItem(tokenA, "Temporal 1", "unidad", 0).get("id").asText();
    mockMvc.perform(
            delete("/api/v1/inventory/items/{id}", sinMovimientos)
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNoContent());

    String conMovimientos = crearItem(tokenA, "Temporal 2", "unidad", 0).get("id").asText();
    moverStock(tokenA, conMovimientos, 5, "Compra");
    mockMvc.perform(
            delete("/api/v1/inventory/items/{id}", conMovimientos)
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isConflict());
  }

  // ---------------------------------------------------------------------------
  // Test de stock negativo (DoD: 409 claro, stock intacto)
  // ---------------------------------------------------------------------------

  @Test
  void consumoQueDejaNegativoDevuelve409YStockIntacto() throws Exception {
    String itemId = crearItem(tokenA, "Implante", "unidad", 1).get("id").asText();
    moverStock(tokenA, itemId, 2, "Compra");

    Map<String, Object> exceso = Map.of("quantityDelta", -5, "reason", "Consumo excesivo");
    MvcResult result = mockMvc.perform(
            post("/api/v1/inventory/items/{id}/movements", itemId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exceso)))
        .andExpect(status().isConflict())
        .andReturn();
    assertThat(result.getResponse().getContentAsString()).contains("Stock insuficiente");

    assertThat(verItem(tokenA, itemId).get("quantity").asInt()).isEqualTo(2);
  }

  @Test
  void deltaCeroDevuelve400() throws Exception {
    String itemId = crearItem(tokenA, "Sutura", "unidad", 2).get("id").asText();

    Map<String, Object> body = Map.of("quantityDelta", 0, "reason", "Sin efecto");
    mockMvc.perform(
            post("/api/v1/inventory/items/{id}/movements", itemId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant y autenticación
  // ---------------------------------------------------------------------------

  @Test
  void itemDeOtroTenantDevuelve404() throws Exception {
    String itemIdA = crearItem(tokenA, "Corona", "unidad", 2).get("id").asText();

    // Ver y mover con el token de B → como si no existiera.
    mockMvc.perform(
            get("/api/v1/inventory/items/{id}", itemIdA)
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    Map<String, Object> body = Map.of("quantityDelta", 5, "reason", "Intento");
    mockMvc.perform(
            post("/api/v1/inventory/items/{id}/movements", itemIdA)
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());

    // Críticos de B no incluyen nada de A.
    MvcResult criticosB = mockMvc.perform(
            get("/api/v1/inventory/critical")
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(criticosB.getResponse().getContentAsString()).doesNotContain(itemIdA);
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/inventory/items"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/inventory/critical"))
        .andExpect(status().isUnauthorized());
  }
}
