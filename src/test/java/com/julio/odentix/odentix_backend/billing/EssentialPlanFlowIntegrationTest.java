package com.julio.odentix.odentix_backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.dto.UpdateAppointmentStatusRequest;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.billing.dto.CreateInvoiceRequest;
import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentRequest;
import com.julio.odentix.odentix_backend.billing.entity.PaymentMethod;
import com.julio.odentix.odentix_backend.patient.dto.CreateClinicalRecordRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreateOdontogramEntryRequest;
import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanItemRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanStatusRequest;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prueba de integración de demo de punta a punta para el Checkpoint del Plan Esencial (FASE4-05).
 *
 * <p>Verifica el flujo operativo y financiero completo de una clínica odontológica pequeña
 * exclusivamente vía API REST:
 * <ol>
 *   <li>Creación de paciente.</li>
 *   <li>Agendamiento de cita de valoración.</li>
 *   <li>Confirmación de la cita.</li>
 *   <li>Registro de anamnesis/diagnóstico en historia clínica.</li>
 *   <li>Registro de odontograma inicial.</li>
 *   <li>Atención de la cita (estado {@code atendida}).</li>
 *   <li>Propuesta de plan de tratamiento con ítems y procedimientos en notación FDI.</li>
 *   <li>Avance de la máquina de estados comercial: {@code borrador} → {@code presentado} → {@code en_decision} → {@code aceptado} → {@code en_ejecucion}.</li>
 *   <li>Generación de factura a partir del plan de tratamiento aceptado.</li>
 *   <li>Abono inicial parcial (factura pasa a {@code parcial}).</li>
 *   <li>Pago final completando el 100% del saldo (factura pasa automáticamente a {@code pagada}).</li>
 *   <li>Finalización del tratamiento (estado {@code completado}).</li>
 * </ol>
 *
 * <p>Criterio de aceptación FASE4-05: La demo completa corre sin intervención manual en la base de datos (todo vía API).
 */
@AutoConfigureMockMvc
class EssentialPlanFlowIntegrationTest extends AbstractIntegrationTest {

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

  private Tenant tenant;
  private String tokenOwner;
  private String tokenRecepcion;
  private Professional professional;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    // 1. Configuración de la clínica (1 sede, 2 usuarios: coherente con plan Esencial)
    tenant = tenantRepository.save(new Tenant("Clínica Dental Esencial", "901234567-8"));

    User owner = userService.createUser(
        tenant.getId(), "propietario@esencial.com", "ClaveSegura123!", "Dr. Roberto Propietario", UserRole.propietario);
    tokenOwner = jwtService.generateToken(owner);

    User recepcion = userService.createUser(
        tenant.getId(), "recepcion@esencial.com", "ClaveSegura123!", "Ana Gómez Recepcionista", UserRole.recepcion);
    tokenRecepcion = jwtService.generateToken(recepcion);

    TenantContext.setTenantId(tenant.getId());
    try {
      Professional prof = new Professional(tenant.getId(), "Dr. Roberto Propietario");
      prof.setUser(owner);
      prof.setSpecialty("Odontología General e Integral");
      prof.setLicenseNumber("TP-554433");
      prof.setExternal(false);
      professional = professionalRepository.save(prof);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  @DisplayName("Demo completa Plan Esencial: Paciente → Cita → Historia Clínica → Odontograma → Plan de Tratamiento → Factura → Pagos")
  void demoPuntaAPuntaPlanEsencialExitoso() throws Exception {

    // =========================================================================
    // PASO 1: Recepción registra un nuevo paciente
    // =========================================================================
    CreatePatientRequest patientReq = new CreatePatientRequest();
    patientReq.setFirstName("Juan");
    patientReq.setLastName("Valdez");
    patientReq.setDocumentType("CC");
    patientReq.setDocumentNumber("1098765432");
    patientReq.setPhone("+573001112233");
    patientReq.setEmail("juan.valdez@cafe.com");

    MvcResult patientResult = mockMvc.perform(post("/api/v1/patients")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(patientReq)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.firstName").value("Juan"))
        .andReturn();

    UUID patientId = extractId(patientResult);

    // =========================================================================
    // PASO 2: Recepción agenda una cita de valoración para el día siguiente
    // =========================================================================
    Instant startsAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
    Instant endsAt = startsAt.plus(30, ChronoUnit.MINUTES);

    CreateAppointmentRequest appointmentReq = new CreateAppointmentRequest(
        patientId, professional.getId(), startsAt, endsAt);
    appointmentReq.setEstimatedValueCop(new BigDecimal("120000.00"));
    appointmentReq.setNotes("Primera consulta de valoración y diagnóstico");

    MvcResult appointmentResult = mockMvc.perform(post("/api/v1/appointments")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(appointmentReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("programada"))
        .andReturn();

    UUID appointmentId = extractId(appointmentResult);

    // =========================================================================
    // PASO 3: Recepción confirma la cita telefónicamente con el paciente
    // =========================================================================
    mockMvc.perform(patch("/api/v1/appointments/{id}/status", appointmentId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateAppointmentStatusRequest(AppointmentStatus.confirmada))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("confirmada"));

    // =========================================================================
    // PASO 4: Odontólogo atiende al paciente y registra evolución clínica
    // =========================================================================
    CreateClinicalRecordRequest clinicalReq = new CreateClinicalRecordRequest();
    clinicalReq.setChiefComplaint("Dolor agudo en molar inferior derecho al masticar");
    clinicalReq.setAnamnesis("Sin alergias declaradas. No toma medicamentos anticoagulantes.");
    clinicalReq.setDiagnosis("Caries profunda cavitada en pieza 46 con compromiso pulpar irreversible.");
    clinicalReq.setEvolution("Se realiza apertura de urgencia y se planifica endodoncia birradicular y reconstrucción.");

    mockMvc.perform(post("/api/v1/patients/{id}/clinical-records", patientId)
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(clinicalReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.diagnosis").value(org.hamcrest.Matchers.containsString("Caries profunda")));

    // =========================================================================
    // PASO 5: Odontólogo registra hallazgo en el odontograma (notación FDI: 46)
    // =========================================================================
    CreateOdontogramEntryRequest odontogramReq = new CreateOdontogramEntryRequest();
    odontogramReq.setToothNumber(46);
    odontogramReq.setSurface("oclusal-distal");
    odontogramReq.setEntryType(OdontogramEntryType.diagnostico);
    odontogramReq.setCondition("caries");
    odontogramReq.setNotes("Compromiso pulpar confirmado radiográficamente");

    mockMvc.perform(post("/api/v1/patients/{id}/odontogram", patientId)
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(odontogramReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.toothNumber").value(46));

    // =========================================================================
    // PASO 6: La cita queda atendida
    // =========================================================================
    mockMvc.perform(patch("/api/v1/appointments/{id}/status", appointmentId)
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateAppointmentStatusRequest(AppointmentStatus.atendida))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("atendida"));

    // =========================================================================
    // PASO 7: Odontólogo formula el Plan de Tratamiento formal con procedimientos
    // =========================================================================
    CreateTreatmentPlanItemRequest item1 = new CreateTreatmentPlanItemRequest(
        null, (short) 46, new BigDecimal("350000.00"), new BigDecimal("50000.00")); // Endodoncia con descuento (net 300.000)
    CreateTreatmentPlanItemRequest item2 = new CreateTreatmentPlanItemRequest(
        null, (short) 46, new BigDecimal("450000.00"), BigDecimal.ZERO);            // Corona porcelana (net 450.000)

    CreateTreatmentPlanRequest planReq = new CreateTreatmentPlanRequest(
        patientId,
        professional.getId(),
        "Tratamiento integral de conductos y corona definitiva pieza 46",
        List.of(item1, item2)
    );

    MvcResult planResult = mockMvc.perform(post("/api/v1/treatment-plans")
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(planReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("borrador"))
        .andExpect(jsonPath("$.totalPriceCop").value(750000.00))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andReturn();

    UUID planId = extractId(planResult);

    // =========================================================================
    // PASO 8: Embudo comercial: Presentación, Decisión y Aceptación por el Paciente
    // =========================================================================
    // 8.1 Presentado
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.presentado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("presentado"))
        .andExpect(jsonPath("$.presentedAt").isNotEmpty());

    // 8.2 En decisión
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.en_decision))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("en_decision"));

    // 8.3 Aceptado por el paciente
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.aceptado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("aceptado"));

    // 8.4 Inicia ejecución clínica
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.en_ejecucion))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("en_ejecucion"));

    // =========================================================================
    // PASO 9: Recepción genera la factura vinculada al plan de tratamiento
    // =========================================================================
    CreateInvoiceRequest invoiceReq = new CreateInvoiceRequest();
    invoiceReq.setTreatmentPlanId(planId);

    MvcResult invoiceResult = mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(invoiceReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNotEmpty())
        .andExpect(jsonPath("$.patientId").value(patientId.toString()))
        .andExpect(jsonPath("$.treatmentPlanId").value(planId.toString()))
        .andExpect(jsonPath("$.status").value("pendiente"))
        .andExpect(jsonPath("$.totalCop").value(750000.00))
        .andReturn();

    UUID invoiceId = extractId(invoiceResult);

    // =========================================================================
    // PASO 10: Recepción registra primer abono/pago parcial ($300.000 COP)
    // =========================================================================
    CreatePaymentRequest payment1 = new CreatePaymentRequest(new BigDecimal("300000.00"), PaymentMethod.transferencia);
    payment1.setReference("TRANSF-NEQUI-10928");

    mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payment1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.invoiceStatus").value("parcial"))
        .andExpect(jsonPath("$.amountCop").value(300000.00));

    // =========================================================================
    // PASO 11: Recepción registra el segundo y último pago liquidando el total ($450.000 COP)
    // =========================================================================
    CreatePaymentRequest payment2 = new CreatePaymentRequest(new BigDecimal("450000.00"), PaymentMethod.tarjeta);
    payment2.setReference("DATAFONO-VISA-4433");

    mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(payment2)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.invoiceStatus").value("pagada"))
        .andExpect(jsonPath("$.amountCop").value(450000.00));

    // =========================================================================
    // PASO 12: Con el tratamiento realizado y saldado, el odontólogo completa el plan
    // =========================================================================
    mockMvc.perform(patch("/api/v1/treatment-plans/{id}/status", planId)
            .header("Authorization", "Bearer " + tokenOwner)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(new UpdateTreatmentPlanStatusRequest(TreatmentPlanStatus.completado))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("completado"));

    // =========================================================================
    // PASO 13: Verificación de consistencia final (sobrepagos no permitidos)
    // =========================================================================
    CreatePaymentRequest sobrepago = new CreatePaymentRequest(new BigDecimal("50000.00"), PaymentMethod.efectivo);
    mockMvc.perform(post("/api/v1/invoices/{id}/payments", invoiceId)
            .header("Authorization", "Bearer " + tokenRecepcion)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(sobrepago)))
        .andExpect(status().isBadRequest());
  }

  private UUID extractId(MvcResult result) throws Exception {
    String json = result.getResponse().getContentAsString();
    JsonNode node = objectMapper.readTree(json);
    assertThat(node.has("id")).isTrue();
    return UUID.fromString(node.get("id").asText());
  }
}
