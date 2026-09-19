package com.julio.odentix.odentix_backend.opportunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.opportunity.service.LeadUnrespondedJob;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de acciones sugeridas y su ejecución (FASE9-03) contra PostgreSQL
 * real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Una oportunidad detectada trae sus acciones asociadas (tarea + mensaje).</li>
 *   <li>Ejecutar la tarea crea la `Task` vinculada; re-ejecutar → 409.</li>
 *   <li>Ejecutar el mensaje envía y registra la `Notification`; sin
 *       destinatario fresco → 409.</li>
 *   <li>Acción de otro tenant → 404.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class OpportunityActionIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private LeadRepository leadRepository;

  @Autowired
  private LeadUnrespondedJob leadUnrespondedJob;

  @Autowired
  private TaskRepository taskRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Acción Alfa", "9G0111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@accion-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);

    tenantB = tenantRepository.save(new Tenant("Clínica Acción Beta", "9G0133444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@accion-beta.com", "ClaveSegura456!", "Recepción Beta",
        UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Lead crearLeadAntiguo(UUID tenantId, String nombre, String telefono, String email) {
    TenantContext.setTenantId(tenantId);
    try {
      Lead lead = new Lead(tenantId, nombre, telefono, email, "instagram");
      lead.setEstimatedValueCop(new BigDecimal("800000.00"));
      lead.setCreatedAt(Instant.now().minus(30, ChronoUnit.HOURS));
      return leadRepository.saveAndFlush(lead);
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode bandeja(String token) throws Exception {
    MvcResult result = mockMvc.perform(get("/api/v1/opportunities")
            .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode ejecutar(String token, String oppId, String actionId, int esperado)
      throws Exception {
    MvcResult result = mockMvc.perform(
            post("/api/v1/opportunities/{id}/actions/{actionId}/execute", oppId, actionId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().is(esperado))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  private List<Task> tareasDe(UUID tenantId) {
    TenantContext.setTenantId(tenantId);
    try {
      return taskRepository.findAll();
    } finally {
      TenantContext.clear();
    }
  }

  private List<Notification> notificacionesDe(UUID tenantId) {
    TenantContext.setTenantId(tenantId);
    try {
      return notificationRepository.findAll();
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests (DoD FASE9-03)
  // ---------------------------------------------------------------------------

  @Test
  void oportunidadTraeAccionesYEjecutarTareaCreaTask() throws Exception {
    Lead lead = crearLeadAntiguo(tenantA.getId(), "Acción Tarea", "573002223344", "tarea@x.com");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    // La bandeja trae la oportunidad con sus 2 acciones (tarea + mensaje).
    JsonNode bandeja = bandeja(tokenA);
    assertThat(bandeja).hasSize(1);
    JsonNode acciones = bandeja.get(0).get("actions");
    assertThat(acciones).hasSize(2);
    String oppId = bandeja.get(0).get("id").asText();
    String tareaId = null;
    String mensajeId = null;
    for (JsonNode accion : acciones) {
      if (accion.get("actionType").asText().equals("crear_tarea")) {
        tareaId = accion.get("id").asText();
      } else {
        mensajeId = accion.get("id").asText();
        assertThat(accion.get("channel").asText()).isEqualTo("whatsapp");
      }
    }
    assertThat(tareaId).isNotNull();
    assertThat(mensajeId).isNotNull();

    // Ejecutar la tarea crea la Task vinculada a la oportunidad.
    JsonNode ejecutada = ejecutar(tokenA, oppId, tareaId, 200);
    assertThat(ejecutada.get("executed").asBoolean()).isTrue();
    assertThat(ejecutada.get("taskId").asText()).isNotBlank();

    List<Task> tareas = tareasDe(tenantA.getId());
    assertThat(tareas).hasSize(1);
    assertThat(tareas.get(0).getRelatedEntityType()).isEqualTo("opportunity");
    assertThat(tareas.get(0).getRelatedEntityId().toString()).isEqualTo(oppId);
    assertThat(tareas.get(0).getTitle()).contains("Acción Tarea");

    // Re-ejecutar → 409.
    ejecutar(tokenA, oppId, tareaId, 409);

    // El lead sigue identificable para el mensaje (evita warning de no-uso).
    assertThat(lead.getId()).isNotNull();
  }

  @Test
  void ejecutarMensajeEnviaYRegistraNotificacion() throws Exception {
    crearLeadAntiguo(tenantA.getId(), "Acción Mensaje", "573003334455", "mensaje@x.com");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    JsonNode bandeja = bandeja(tokenA);
    String oppId = bandeja.get(0).get("id").asText();
    String mensajeId = null;
    for (JsonNode accion : bandeja.get(0).get("actions")) {
      if (accion.get("actionType").asText().equals("enviar_mensaje")) {
        mensajeId = accion.get("id").asText();
      }
    }
    assertThat(mensajeId).isNotNull();

    // WhatsApp está apagado en tests: el envío queda fallida, pero la acción
    // se marca ejecutada porque el intento fue real (nunca-lanza).
    JsonNode ejecutada = ejecutar(tokenA, oppId, mensajeId, 200);
    assertThat(ejecutada.get("executed").asBoolean()).isTrue();
    assertThat(ejecutada.get("notificationId").asText()).isNotBlank();

    List<Notification> notificaciones = notificacionesDe(tenantA.getId());
    assertThat(notificaciones).hasSize(1);
    assertThat(notificaciones.get(0).getChannel().name()).isEqualTo("whatsapp");
    assertThat(notificaciones.get(0).getRecipient()).isEqualTo("573003334455");
  }

  @Test
  void mensajeSinDestinatarioFrescoDevuelve409() throws Exception {
    Lead lead = crearLeadAntiguo(tenantA.getId(), "Sin Contacto", "573004445566", "c@x.com");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    // El contacto se pierde después de generar la sugerencia.
    TenantContext.setTenantId(tenantA.getId());
    try {
      lead.setPhone(null);
      lead.setEmail(null);
      leadRepository.saveAndFlush(lead);
    } finally {
      TenantContext.clear();
    }

    JsonNode bandeja = bandeja(tokenA);
    String oppId = bandeja.get(0).get("id").asText();
    String mensajeId = null;
    for (JsonNode accion : bandeja.get(0).get("actions")) {
      if (accion.get("actionType").asText().equals("enviar_mensaje")) {
        mensajeId = accion.get("id").asText();
      }
    }
    assertThat(mensajeId).isNotNull();

    ejecutar(tokenA, oppId, mensajeId, 409);
  }

  @Test
  void accionDeOtroTenantDevuelve404() throws Exception {
    crearLeadAntiguo(tenantB.getId(), "Lead Beta", "573005556677", "beta@x.com");

    TenantContext.clear();
    leadUnrespondedJob.execute();
    TenantContext.clear();

    JsonNode bandejaB = bandeja(tokenB);
    String oppIdB = bandejaB.get(0).get("id").asText();
    String accionIdB = bandejaB.get(0).get("actions").get(0).get("id").asText();

    // Con el token de A, la oportunidad y la acción de B no existen.
    mockMvc.perform(
            post("/api/v1/opportunities/{id}/actions/{actionId}/execute", oppIdB, accionIdB)
                .header("Authorization", "Bearer " + tokenA))
        .andExpect(status().isNotFound());
  }
}
