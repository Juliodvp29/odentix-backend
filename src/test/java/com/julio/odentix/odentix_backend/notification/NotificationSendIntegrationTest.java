package com.julio.odentix.odentix_backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Pruebas de integración HTTP para el envío de mensajes revisados
 * ({@code POST /api/v1/notifications/send}) contra PostgreSQL real vía
 * Testcontainers.
 *
 * <p>Cubre: envío por email con destinatario resuelto en el servidor,
 * intento {@code fallida} auditable sin contacto, aislamiento cross-tenant
 * (regla §5), rol no autorizado y validación del cuerpo.
 */
@AutoConfigureMockMvc
class NotificationSendIntegrationTest extends AbstractIntegrationTest {

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

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private AppointmentRepository appointmentRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenRecepcionA;
  private String tokenEspecialistaA;
  private String tokenPropietarioB;
  private Appointment citaEmailA;
  private Appointment citaSinContactoA;
  private Appointment citaB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Envío Alfa", "9K0111222-1"));
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@envio-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@envio-alfa.com", "ClaveSegura123!", "Ed Externo",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    citaEmailA = sembrarCita(tenantA.getId(), "Elisa", "Mail", "elisa@mail.com", null);
    citaSinContactoA = sembrarCita(tenantA.getId(), "Sin", "Contacto", null, null);

    tenantB = tenantRepository.save(new Tenant("Clínica Envío Beta", "9K0133444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@envio-beta.com", "ClaveSegura456!", "Beto Propietario",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);
    citaB = sembrarCita(tenantB.getId(), "Ajena", "Beta", "ajena@beta.com", null);
  }

  private Appointment sembrarCita(
      UUID tenantId, String nombre, String apellido, String email, String telefono) {
    TenantContext.setTenantId(tenantId);
    try {
      Patient paciente = patientRepository.save(new Patient(tenantId, nombre, apellido));
      paciente.setEmail(email);
      paciente.setPhone(telefono);
      paciente = patientRepository.save(paciente);
      Professional profesional =
          professionalRepository.saveAndFlush(new Professional(tenantId, "Dr. Envío"));
      Instant inicio = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(5, ChronoUnit.HOURS);
      return appointmentRepository.saveAndFlush(new Appointment(tenantId, paciente, profesional,
          inicio, inicio.plus(1, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }
  }

  private JsonNode enviar(String token, UUID citaId, String channel, String body)
      throws Exception {
    Map<String, Object> request = new HashMap<>();
    if (citaId != null) {
      request.put("appointmentId", citaId.toString());
    }
    request.put("channel", channel);
    request.put("body", body);
    MvcResult result = mockMvc.perform(
            post("/api/v1/notifications/send")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void enviarEmailResuelveDestinatarioEnServidor() throws Exception {
    JsonNode intento = enviar(tokenRecepcionA, citaEmailA.getId(), "email",
        "Hola Elisa, te esperamos mañana.");

    assertThat(intento.get("status").asText()).isEqualTo("enviada");
    assertThat(intento.get("channel").asText()).isEqualTo("email");
    assertThat(intento.get("recipient").asText()).isEqualTo("elisa@mail.com");
    assertThat(intento.get("templateKey").asText()).isEqualTo("mensaje_revisado");
  }

  @Test
  void sinContactoRegistraFallidaConDetalle() throws Exception {
    JsonNode intento = enviar(tokenRecepcionA, citaSinContactoA.getId(), "email",
        "Hola, te esperamos mañana.");

    assertThat(intento.get("status").asText()).isEqualTo("fallida");
    assertThat(intento.get("errorDetail").asText()).contains("email");
  }

  @Test
  void citaAjenaDevuelveNotFound() throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("appointmentId", citaB.getId().toString());
    request.put("channel", "email");
    request.put("body", "Hola.");
    mockMvc.perform(
            post("/api/v1/notifications/send")
                .header("Authorization", "Bearer " + tokenRecepcionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  void especialistaExternoRecibeForbidden() throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("appointmentId", citaEmailA.getId().toString());
    request.put("channel", "email");
    request.put("body", "Hola.");
    mockMvc.perform(
            post("/api/v1/notifications/send")
                .header("Authorization", "Bearer " + tokenEspecialistaA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  void cuerpoVacioDevuelveBadRequest() throws Exception {
    Map<String, Object> request = new HashMap<>();
    request.put("appointmentId", citaEmailA.getId().toString());
    request.put("channel", "email");
    request.put("body", "  ");
    mockMvc.perform(
            post("/api/v1/notifications/send")
                .header("Authorization", "Bearer " + tokenRecepcionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());
  }
}
