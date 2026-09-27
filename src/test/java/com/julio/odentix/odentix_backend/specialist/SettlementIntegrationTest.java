package com.julio.odentix.odentix_backend.specialist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * Pruebas de integración HTTP para liquidaciones de especialistas (FASE7-02)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Generar una liquidación calcula producción bruta y honorarios a partir
 *       de las facturas emitidas en el periodo vinculadas a tratamientos del
 *       profesional (excluye anuladas, fuera de periodo, de otro profesional
 *       y sin tratamiento).</li>
 *   <li>Un mismo periodo solo se liquida una vez (409 en el segundo intento).</li>
 *   <li>Aislamiento cross-tenant: liquidar un especialista de otro tenant → 404
 *       (regla de aislamiento multi-tenant del proyecto).</li>
 *   <li>Autorización por rol: solo PROPIETARIO liquida (recepción → 403).</li>
 *   <li>Lecturas: lista de especialistas del tenant, lista y detalle de
 *       liquidaciones por especialista, y desglose con las facturas que
 *       componen el bruto (la suma de líneas cierra con la liquidación).</li>
 *   <li>Lecturas con aislamiento: recursos de otro tenant → 404, liquidación
 *       bajo otro especialista → 404, recepción en lecturas → 403.</li>
 * </ul>
 */
@AutoConfigureMockMvc
class SettlementIntegrationTest extends AbstractIntegrationTest {

  private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

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

  @Autowired
  private SpecialistRepository specialistRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private InvoiceRepository invoiceRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenRecepcionA;
  private String tokenPropietarioB;
  private Specialist specialistA;
  private Specialist specialistB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Liquidación Alfa", "940111222-1"));
    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@liq-alfa.com", "ClaveSegura123!", "Propietario Alfa",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);
    User recepcionA = userService.createUser(
        tenantA.getId(), "recepcion@liq-alfa.com", "ClaveSegura123!", "Recepción Alfa",
        UserRole.recepcion);
    tokenRecepcionA = jwtService.generateToken(recepcionA);

    tenantB = tenantRepository.save(new Tenant("Clínica Liquidación Beta", "941333444-2"));
    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@liq-beta.com", "ClaveSegura456!", "Propietario Beta",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Professional externoA = new Professional(tenantA.getId(), "Dra. Externa Liquida");
      externoA.setExternal(true);
      externoA = professionalRepository.saveAndFlush(externoA);
      specialistA = specialistRepository.saveAndFlush(
          new Specialist(tenantA.getId(), externoA, new BigDecimal("40.00")));

      Professional otroProfesionalA = new Professional(tenantA.getId(), "Dr. Planta Liquida");
      otroProfesionalA.setExternal(false);
      otroProfesionalA = professionalRepository.saveAndFlush(otroProfesionalA);

      Patient pacienteA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
      TreatmentPlan planA =
          treatmentPlanRepository.save(new TreatmentPlan(tenantA.getId(), pacienteA, externoA, "dx"));
      TreatmentPlan planOtro = treatmentPlanRepository.save(
          new TreatmentPlan(tenantA.getId(), pacienteA, otroProfesionalA, "dx otro"));

      // Dentro del periodo y del profesional → cuentan (500k pagada + 300k parcial).
      crearFactura(pacienteA, planA, "LIQ-001", InvoiceStatus.pagada,
          new BigDecimal("500000.00"), instante(2026, 9, 10));
      crearFactura(pacienteA, planA, "LIQ-002", InvoiceStatus.parcial,
          new BigDecimal("300000.00"), instante(2026, 9, 20));
      // Fuera del periodo → no cuenta.
      crearFactura(pacienteA, planA, "LIQ-003", InvoiceStatus.pendiente,
          new BigDecimal("100000.00"), instante(2026, 10, 5));
      // Anulada en periodo → no cuenta.
      crearFactura(pacienteA, planA, "LIQ-004", InvoiceStatus.anulada,
          new BigDecimal("700000.00"), instante(2026, 9, 15));
      // Otro profesional → no cuenta.
      crearFactura(pacienteA, planOtro, "LIQ-005", InvoiceStatus.pagada,
          new BigDecimal("900000.00"), instante(2026, 9, 12));
      // Sin tratamiento asociado → no cuenta.
      crearFactura(pacienteA, null, "LIQ-006", InvoiceStatus.pagada,
          new BigDecimal("400000.00"), instante(2026, 9, 12));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Professional externoB = new Professional(tenantB.getId(), "Dr. Externo Beta");
      externoB.setExternal(true);
      externoB = professionalRepository.saveAndFlush(externoB);
      specialistB = specialistRepository.saveAndFlush(
          new Specialist(tenantB.getId(), externoB, new BigDecimal("50.00")));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static Instant instante(int anio, int mes, int dia) {
    return LocalDate.of(anio, mes, dia).atTime(10, 0).atZone(BOGOTA).toInstant();
  }

  private void crearFactura(Patient paciente, TreatmentPlan plan, String numero,
      InvoiceStatus estado, BigDecimal total, Instant emitido) {
    Invoice factura = new Invoice();
    factura.setTenantId(paciente.getTenantId());
    factura.setPatient(paciente);
    factura.setTreatmentPlan(plan);
    factura.setInvoiceNumber(numero);
    factura.setStatus(estado);
    factura.setSubtotalCop(total);
    factura.setTotalCop(total);
    factura.setIssuedAt(emitido);
    invoiceRepository.save(factura);
  }

  private JsonNode liquidar(String token, UUID specialistId,
      String inicio, String fin) throws Exception {
    Map<String, Object> body = Map.of("periodStart", inicio, "periodEnd", fin);
    MvcResult result = mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE7-02)
  // ---------------------------------------------------------------------------

  @Test
  void generarLiquidacionCalculaBrutoYHonorarios() throws Exception {
    JsonNode json = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");

    // Bruto = 500k + 300k (excluye anulada, fuera de periodo, otro
    // profesional y sin tratamiento); honorarios = 40 %.
    assertThat(new BigDecimal(json.get("grossProductionCop").asText()))
        .isEqualByComparingTo(new BigDecimal("800000.00"));
    assertThat(new BigDecimal(json.get("feeAmountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("320000.00"));
    assertThat(json.get("status").asText()).isEqualTo("pendiente");
    assertThat(json.get("specialistId").asText()).isEqualTo(specialistA.getId().toString());
    assertThat(json.get("periodStart").asText()).isEqualTo("2026-09-01");
    assertThat(json.get("periodEnd").asText()).isEqualTo("2026-09-30");
  }

  @Test
  void segundaLiquidacionDelMismoPeriodoDevuelve409() throws Exception {
    liquidar(tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");

    Map<String, Object> body = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
  }

  @Test
  void periodoDistintoDevuelve201ConSuPropioCalculo() throws Exception {
    // Octubre solo tiene la factura pendiente de 100k → bruto 100k, honorarios 40k.
    JsonNode json = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-10-01", "2026-10-31");

    assertThat(new BigDecimal(json.get("grossProductionCop").asText()))
        .isEqualByComparingTo(new BigDecimal("100000.00"));
    assertThat(new BigDecimal(json.get("feeAmountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("40000.00"));
  }

  @Test
  void periodoInvertidoDevuelve400() throws Exception {
    Map<String, Object> body = Map.of("periodStart", "2026-09-30", "periodEnd", "2026-09-01");
    mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenPropietarioA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant y autorización por rol
  // ---------------------------------------------------------------------------

  @Test
  void especialistaDeOtroTenantDevuelve404() throws Exception {
    // Propietario de B intenta liquidar al especialista de A → como si no existiera.
    Map<String, Object> body = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenPropietarioB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isNotFound());

    // Y el especialista de B sí se puede liquidar (bruto cero, sin facturas).
    JsonNode json = liquidar(
        tokenPropietarioB, specialistB.getId(), "2026-09-01", "2026-09-30");
    assertThat(new BigDecimal(json.get("grossProductionCop").asText()))
        .isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(new BigDecimal(json.get("feeAmountCop").asText()))
        .isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void recepcionNoPuedeLiquidarDevuelve403() throws Exception {
    Map<String, Object> body = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenRecepcionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    Map<String, Object> body = Map.of("periodStart", "2026-09-01", "periodEnd", "2026-09-30");
    mockMvc.perform(
            post("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isUnauthorized());
  }

  // ---------------------------------------------------------------------------
  // Tests de lectura: lista de especialistas, liquidaciones y desglose
  // ---------------------------------------------------------------------------

  @Test
  void listarEspecialistasDevuelveSoloLosDelTenant() throws Exception {
    MvcResult resultA = mockMvc.perform(
            get("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode listaA = objectMapper.readTree(resultA.getResponse().getContentAsString());
    assertThat(listaA).hasSize(1);
    assertThat(listaA.get(0).get("id").asText()).isEqualTo(specialistA.getId().toString());
    assertThat(listaA.get(0).get("fullName").asText()).isEqualTo("Dra. Externa Liquida");
    assertThat(new BigDecimal(listaA.get(0).get("feePercentage").asText()))
        .isEqualByComparingTo(new BigDecimal("40.00"));

    // El propietario B solo ve su propio especialista (aislamiento por listado).
    MvcResult resultB = mockMvc.perform(
            get("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode listaB = objectMapper.readTree(resultB.getResponse().getContentAsString());
    assertThat(listaB).hasSize(1);
    assertThat(listaB.get(0).get("id").asText()).isEqualTo(specialistB.getId().toString());
  }

  @Test
  void listarLiquidacionesDevuelveOrdenadasPorPeriodo() throws Exception {
    JsonNode sept = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");
    JsonNode oct = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-10-01", "2026-10-31");

    MvcResult result = mockMvc.perform(
            get("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(lista).hasSize(2);
    assertThat(lista.get(0).get("id").asText()).isEqualTo(sept.get("id").asText());
    assertThat(lista.get(0).get("periodStart").asText()).isEqualTo("2026-09-01");
    assertThat(lista.get(1).get("id").asText()).isEqualTo(oct.get("id").asText());
    assertThat(lista.get(1).get("periodStart").asText()).isEqualTo("2026-10-01");
  }

  @Test
  void listarLiquidacionesSinDatosDevuelveArregloVacio() throws Exception {
    // El especialista B no tiene liquidaciones: 200 con [] (no 404).
    MvcResult result = mockMvc.perform(
            get("/api/v1/specialists/{id}/settlements", specialistB.getId())
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode lista = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(lista).hasSize(0);
  }

  @Test
  void consultarLiquidacionDevuelveElDetalle() throws Exception {
    JsonNode creada = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");

    MvcResult result = mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}",
                specialistA.getId(), creada.get("id").asText())
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    assertThat(json.get("id").asText()).isEqualTo(creada.get("id").asText());
    assertThat(json.get("specialistId").asText()).isEqualTo(specialistA.getId().toString());
    assertThat(new BigDecimal(json.get("grossProductionCop").asText()))
        .isEqualByComparingTo(new BigDecimal("800000.00"));
    assertThat(new BigDecimal(json.get("feeAmountCop").asText()))
        .isEqualByComparingTo(new BigDecimal("320000.00"));
    assertThat(json.get("status").asText()).isEqualTo("pendiente");
  }

  @Test
  void desgloseListaLasFacturasQueComponenElBruto() throws Exception {
    JsonNode creada = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");

    MvcResult result = mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}/breakdown",
                specialistA.getId(), creada.get("id").asText())
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());

    // Solo LIQ-001 y LIQ-002 componen el bruto (excluye anulada, fuera de
    // periodo, otro profesional y sin tratamiento), ordenadas por emisión.
    assertThat(json.get("settlementId").asText()).isEqualTo(creada.get("id").asText());
    assertThat(json.get("invoiceCount").asInt()).isEqualTo(2);
    JsonNode lineas = json.get("invoices");
    assertThat(lineas).hasSize(2);
    assertThat(lineas.get(0).get("invoiceNumber").asText()).isEqualTo("LIQ-001");
    assertThat(new BigDecimal(lineas.get(0).get("totalCop").asText()))
        .isEqualByComparingTo(new BigDecimal("500000.00"));
    assertThat(lineas.get(1).get("invoiceNumber").asText()).isEqualTo("LIQ-002");
    assertThat(new BigDecimal(lineas.get(1).get("totalCop").asText()))
        .isEqualByComparingTo(new BigDecimal("300000.00"));

    // La suma de las líneas cierra con el bruto de la liquidación.
    BigDecimal suma = new BigDecimal(lineas.get(0).get("totalCop").asText())
        .add(new BigDecimal(lineas.get(1).get("totalCop").asText()));
    assertThat(suma).isEqualByComparingTo(
        new BigDecimal(json.get("grossProductionCop").asText()));
  }

  // ---------------------------------------------------------------------------
  // Aislamiento cross-tenant y autorización por rol de las lecturas
  // ---------------------------------------------------------------------------

  @Test
  void lecturasDeOtroTenantDevuelven404() throws Exception {
    JsonNode creada = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");
    String liquidacionA = creada.get("id").asText();

    // Propietario de B sobre el especialista de A: lista, detalle y desglose → 404.
    mockMvc.perform(
            get("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isNotFound());
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}",
                specialistA.getId(), liquidacionA)
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isNotFound());
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}/breakdown",
                specialistA.getId(), liquidacionA)
                .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isNotFound());
  }

  @Test
  void liquidacionDeOtroEspecialistaDelMismoTenantDevuelve404() throws Exception {
    JsonNode creada = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");

    // Segundo especialista en el mismo tenant A.
    TenantContext.setTenantId(tenantA.getId());
    Specialist otro;
    try {
      Professional profesional = new Professional(tenantA.getId(), "Dra. Otra Externa");
      profesional.setExternal(true);
      profesional = professionalRepository.saveAndFlush(profesional);
      otro = specialistRepository.saveAndFlush(
          new Specialist(tenantA.getId(), profesional, new BigDecimal("30.00")));
    } finally {
      TenantContext.clear();
    }

    // La liquidación de A pedida bajo el otro especialista → 404.
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}",
                otro.getId(), creada.get("id").asText())
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isNotFound());
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}/breakdown",
                otro.getId(), creada.get("id").asText())
                .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isNotFound());
  }

  @Test
  void recepcionNoPuedeLeerEspecialistasNiLiquidacionesDevuelve403() throws Exception {
    JsonNode creada = liquidar(
        tokenPropietarioA, specialistA.getId(), "2026-09-01", "2026-09-30");
    String liquidacionA = creada.get("id").asText();

    mockMvc.perform(
            get("/api/v1/specialists")
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isForbidden());
    mockMvc.perform(
            get("/api/v1/specialists/{id}/settlements", specialistA.getId())
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isForbidden());
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}",
                specialistA.getId(), liquidacionA)
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isForbidden());
    mockMvc.perform(
            get("/api/v1/specialists/{specialistId}/settlements/{settlementId}/breakdown",
                specialistA.getId(), liquidacionA)
                .header("Authorization", "Bearer " + tokenRecepcionA))
        .andExpect(status().isForbidden());
  }
}

