package com.julio.odentix.odentix_backend.task;

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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para tareas (FASE8-01) contra PostgreSQL real
 * vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Crear, asignar y completar una tarea manualmente vía API.</li>
 *   <li>Endpoint "mis tareas" filtrado por el usuario autenticado.</li>
 *   <li>Aislamiento cross-tenant: tareas de otro tenant → 404 (regla §5).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class TaskIntegrationTest extends AbstractIntegrationTest {

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
  private User recepcionA1;
  private User recepcionA2;
  private User recepcionB;
  private String tokenA1;
  private String tokenA2;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Tareas Alfa", "970111222-1"));
    recepcionA1 = userService.createUser(
        tenantA.getId(), "recepcion1@tareas-alfa.com", "ClaveSegura123!", "Recepción Uno",
        UserRole.recepcion);
    tokenA1 = jwtService.generateToken(recepcionA1);
    recepcionA2 = userService.createUser(
        tenantA.getId(), "recepcion2@tareas-alfa.com", "ClaveSegura123!", "Recepción Dos",
        UserRole.recepcion);
    tokenA2 = jwtService.generateToken(recepcionA2);

    tenantB = tenantRepository.save(new Tenant("Clínica Tareas Beta", "971333444-2"));
    recepcionB = userService.createUser(
        tenantB.getId(), "recepcion@tareas-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(recepcionB);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private JsonNode crearTarea(String token, String titulo, UUID asignado) throws Exception {
    Map<String, Object> body = new HashMap<>();
    body.put("title", titulo);
    body.put("description", "Llamar para confirmar asistencia");
    body.put("priority", "alta");
    body.put("dueAt", "2026-09-30T17:00:00Z");
    if (asignado != null) {
      body.put("assignedTo", asignado.toString());
    }
    MvcResult result = mockMvc.perform(
            post("/api/v1/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode completarTarea(String token, String tareaId) throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/v1/tasks/{id}/complete", tareaId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode misTareas(String token) throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/tasks/mine")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE8-01)
  // ---------------------------------------------------------------------------

  @Test
  void flujoCrearAsignarYCompletar() throws Exception {
    JsonNode creada = crearTarea(tokenA1, "Confirmar cita pendiente", recepcionA1.getId());

    assertThat(creada.get("status").asText()).isEqualTo("pendiente");
    assertThat(creada.get("title").asText()).isEqualTo("Confirmar cita pendiente");
    assertThat(creada.get("assignedTo").asText()).isEqualTo(recepcionA1.getId().toString());
    assertThat(creada.get("priority").asText()).isEqualTo("alta");
    String tareaId = creada.get("id").asText();

    // Actualizar el título.
    Map<String, Object> edicion = Map.of("title", "Confirmar cita pendiente (urgente)");
    MvcResult editada = mockMvc.perform(
            patch("/api/v1/tasks/{id}", tareaId)
                .header("Authorization", "Bearer " + tokenA1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(edicion)))
        .andExpect(status().isOk())
        .andReturn();
    assertThat(objectMapper.readTree(editada.getResponse().getContentAsString())
        .get("title").asText()).isEqualTo("Confirmar cita pendiente (urgente)");

    // Completar (y recompletar: idempotente).
    assertThat(completarTarea(tokenA1, tareaId).get("status").asText())
        .isEqualTo("completada");
    assertThat(completarTarea(tokenA1, tareaId).get("status").asText())
        .isEqualTo("completada");

    // Eliminar.
    mockMvc.perform(
            delete("/api/v1/tasks/{id}", tareaId)
                .header("Authorization", "Bearer " + tokenA1))
        .andExpect(status().isNoContent());
    mockMvc.perform(
            get("/api/v1/tasks/{id}", tareaId)
                .header("Authorization", "Bearer " + tokenA1))
        .andExpect(status().isNotFound());
  }

  @Test
  void misTareasSoloDevuelveLasPropias() throws Exception {
    String propia = crearTarea(tokenA1, "Tarea de Uno", recepcionA1.getId()).get("id").asText();
    crearTarea(tokenA1, "Tarea de Dos", recepcionA2.getId());
    crearTarea(tokenA1, "Tarea sin asignar", null);

    JsonNode mias = misTareas(tokenA1);
    assertThat(mias).hasSize(1);
    assertThat(mias.get(0).get("id").asText()).isEqualTo(propia);

    JsonNode deDos = misTareas(tokenA2);
    assertThat(deDos).hasSize(1);
    assertThat(deDos.get(0).get("title").asText()).isEqualTo("Tarea de Dos");
  }

  @Test
  void asignarAUsuarioInexistenteOOtroTenantDevuelve404() throws Exception {
    Map<String, Object> fantasma = new HashMap<>();
    fantasma.put("title", "Tarea fantasma");
    fantasma.put("assignedTo", UUID.randomUUID().toString());
    mockMvc.perform(
            post("/api/v1/tasks")
                .header("Authorization", "Bearer " + tokenA1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fantasma)))
        .andExpect(status().isNotFound());

    // Usuario real pero de otra clínica → también 404 (no se filtra el dato).
    Map<String, Object> ajeno = new HashMap<>();
    ajeno.put("title", "Tarea ajena");
    ajeno.put("assignedTo", recepcionB.getId().toString());
    mockMvc.perform(
            post("/api/v1/tasks")
                .header("Authorization", "Bearer " + tokenA1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ajeno)))
        .andExpect(status().isNotFound());
  }

  @Test
  void tituloEnBlancoDevuelve400() throws Exception {
    Map<String, Object> body = Map.of("title", "  ");
    mockMvc.perform(
            post("/api/v1/tasks")
                .header("Authorization", "Bearer " + tokenA1)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant y autenticación
  // ---------------------------------------------------------------------------

  @Test
  void tareaDeOtroTenantDevuelve404() throws Exception {
    String tareaIdA = crearTarea(tokenA1, "Tarea de Alfa", recepcionA1.getId()).get("id").asText();

    // Ver, completar y eliminar con el token de B → como si no existiera.
    mockMvc.perform(
            get("/api/v1/tasks/{id}", tareaIdA)
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
    mockMvc.perform(
            post("/api/v1/tasks/{id}/complete", tareaIdA)
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());
    mockMvc.perform(
            delete("/api/v1/tasks/{id}", tareaIdA)
                .header("Authorization", "Bearer " + tokenB))
        .andExpect(status().isNotFound());

    // "Mis tareas" de B no incluye nada de A.
    assertThat(misTareas(tokenB)).hasSize(0);
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/tasks"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/tasks/mine"))
        .andExpect(status().isUnauthorized());
  }
}
