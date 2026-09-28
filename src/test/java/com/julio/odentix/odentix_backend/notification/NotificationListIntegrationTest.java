package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import com.julio.odentix.odentix_backend.notification.repository.NotificationRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración HTTP para el historial de notificaciones
 * ({@code GET /api/v1/notifications}) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre: página ordenada por fecha descendente con el detalle de error
 * visible en las fallidas, legible por los roles operativos (403 para el
 * especialista externo) y aislamiento cross-tenant (regla §5).
 */
@AutoConfigureMockMvc
class NotificationListIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenEspecialistaA;
  private String tokenPropietarioB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Notif Alfa", "960111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@notif-alfa.com", "ClaveSegura123!", "Pau Propietaria",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@notif-alfa.com", "ClaveSegura123!", "Ana Recepción",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@notif-alfa.com", "ClaveSegura123!", "Ed Externo",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    guardar(tenantA, NotificationChannel.email, "a@alfa.com", "cita_confirmacion",
        NotificationStatus.enviada, null, "2026-09-20T10:00:00Z");
    guardar(tenantA, NotificationChannel.whatsapp, "+573001112233", "cita_recordatorio",
        NotificationStatus.fallida, "Timeout del proveedor", "2026-09-21T10:00:00Z");
    guardar(tenantA, NotificationChannel.email, "b@alfa.com", "cita_confirmacion",
        NotificationStatus.pendiente, null, "2026-09-22T10:00:00Z");

    tenantB = tenantRepository.save(new Tenant("Clínica Notif Beta", "961333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@notif-beta.com", "ClaveSegura456!", "Beto Propietario",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);
    guardar(tenantB, NotificationChannel.sms, "+573004445566", "cita_recordatorio",
        NotificationStatus.enviada, null, "2026-09-21T12:00:00Z");
  }

  private void guardar(
      Tenant tenant, NotificationChannel channel, String recipient, String templateKey,
      NotificationStatus status, String errorDetail, String createdAt) {
    Notification intento = new Notification(tenant.getId(), channel, recipient);
    intento.setTemplateKey(templateKey);
    intento.setStatus(status);
    intento.setErrorDetail(errorDetail);
    intento.setCreatedAt(Instant.parse(createdAt));
    notificationRepository.save(intento);
  }

  @Test
  void listarDevuelvePaginaOrdenadaPorFecha() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/notifications")
                .param("size", "2")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode pagina = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(pagina.get("totalElements").asLong()).isEqualTo(3);
    JsonNode contenido = pagina.get("content");
    assertThat(contenido).hasSize(2);
    assertThat(contenido.get(0).get("templateKey").asText()).isEqualTo("cita_confirmacion");
    assertThat(contenido.get(0).get("status").asText()).isEqualTo("pendiente");
    assertThat(contenido.get(1).get("status").asText()).isEqualTo("fallida");
  }

  @Test
  void fallidaExponeDetalleDeErrorSinLogs() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/notifications")
                .param("size", "10")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode contenido = objectMapper.readTree(result.getResponse().getContentAsString())
        .get("content");

    JsonNode fallida = null;
    for (JsonNode intento : contenido) {
      if ("fallida".equals(intento.get("status").asText())) {
        fallida = intento;
      }
    }
    assertThat(fallida).isNotNull();
    assertThat(fallida.get("channel").asText()).isEqualTo("whatsapp");
    assertThat(fallida.get("recipient").asText()).isEqualTo("+573001112233");
    assertThat(fallida.get("errorDetail").asText()).isEqualTo("Timeout del proveedor");
  }

  @Test
  void recepcionPuedeListar() throws Exception {
    mockMvc.perform(
            get("/api/v1/notifications")
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isOk());
  }

  @Test
  void especialistaExternoRecibeForbidden() throws Exception {
    mockMvc.perform(
            get("/api/v1/notifications")
                .header("Authorization", "Bearer " + tokenEspecialistaA))
        .andExpect(status().isForbidden());
  }

  @Test
  void cadaTenantSoloVeSusNotificaciones() throws Exception {
    MvcResult result = mockMvc.perform(
            get("/api/v1/notifications")
                .param("size", "10")
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode pagina = objectMapper.readTree(result.getResponse().getContentAsString());

    assertThat(pagina.get("totalElements").asLong()).isEqualTo(1);
    assertThat(pagina.get("content").get(0).get("recipient").asText())
        .isEqualTo("+573004445566");
  }
}
