package com.julio.odentix.odentix_backend.billing;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanItemRepository;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Pruebas de integración para facturación y pagos (FASE4-04) contra
 * PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: pagos parciales hasta cubrir el total
 * cambian el estado a `pagada` automáticamente, más aislamiento
 * cross-tenant verificado con test y autorización por rol.
 */
@AutoConfigureMockMvc
class InvoiceIntegrationTest extends AbstractIntegrationTest {

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
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private TreatmentPlanItemRepository treatmentPlanItemRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenA;
  private String tokenB;
  private String tokenEspecialistaA;
  private Patient patientA;
  private Patient patientB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Factura Alfa", "913111222-1"));
    User userA = userService.createUser(
        tenantA.getId(), "recepcion@factura-alfa.com", "ClaveSegura123!", "Recepción Alfa", UserRole.recepcion);
    tokenA = jwtService.generateToken(userA);
    User especialistaA = userService.createUser(
        tenantA.getId(), "externo@factura-alfa.com", "ClaveSegura789!", "Externo Alfa",
        UserRole.especialista_externo);
    tokenEspecialistaA = jwtService.generateToken(especialistaA);

    tenantB = tenantRepository.save(new Tenant("Clínica Factura Beta", "914333444-2"));
    User userB = userService.createUser(
        tenantB.getId(), "recepcion@factura-beta.com", "ClaveSegura456!", "Recepción Beta", UserRole.recepcion);
    tokenB = jwtService.generateToken(userB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      patientB = patientRepository.save(new Patient(tenantB.getId(), "Beto", "Pérez"));
    } finally {
      TenantContext.clear();
    }
  }

  private TreatmentPlan crearPlan(UUID tenantId, Patient patient, String[][] items) {
    TenantContext.setTenantId(tenantId);
    try {
      TreatmentPlan plan = treatmentPlanRepository.save(new TreatmentPlan(tenantId, patient));
      for (String[] item : items) {
        TreatmentPlanItem planItem = new TreatmentPlanItem(
            tenantId, plan, new BigDecimal(item[0]));
        planItem.setDiscountCop(new BigDecimal(item[1]));
        if (item.length > 2) {
          planItem.setToothNumber(Short.valueOf(item[2]));
        }
        treatmentPlanItemRepository.save(planItem);
      }
      return plan;
    } finally {
      TenantContext.clear();
    }
  }

  private Map<String, Object> itemManual(String description, int quantity, String unitPrice) {
    return Map.of("description", description, "quantity", quantity, "unitPriceCop", new BigDecimal(unitPrice));
  }

  private String crearFactura(String token, Map<String, Object> body) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andReturn();
    Map<?, ?> factura = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    return factura.get("id").toString();
  }

  @Test
  void crearFacturaManualCalculaTotales() throws Exception {
    Map<String, Object> body = Map.of(
        "patientId", patientA.getId().toString(),
        "discountCop", new BigDecimal("50000.00"),
        "items", List.of(
            itemManual("Obturación resina", 2, "150000.00"),
            itemManual("Endodoncia", 1, "200000.00")));

    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body)))
        .andExpect(status().isCreated())
        .andExpect(header().exists(HttpHeaders.LOCATION))
        .andExpect(jsonPath("$.invoiceNumber", Matchers.startsWith("FAC-")))
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.treatmentPlanId").doesNotExist())
        .andExpect(jsonPath("$.subtotalCop").value(500000.00))
        .andExpect(jsonPath("$.discountCop").value(50000.00))
        .andExpect(jsonPath("$.totalCop").value(450000.00))
        .andExpect(jsonPath("$.status").value("pendiente"))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].totalCop").value(300000.00))
        .andExpect(jsonPath("$.items[1].totalCop").value(200000.00));
  }

  @Test
  void crearFacturaDesdePlanInfierePacienteEItems() throws Exception {
    TreatmentPlan plan = crearPlan(tenantA.getId(), patientA,
        new String[][]{{"100000.00", "10000.00", "16"}, {"50000.00", "0.00"}});

    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("treatmentPlanId", plan.getId().toString()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.patientId").value(patientA.getId().toString()))
        .andExpect(jsonPath("$.treatmentPlanId").value(plan.getId().toString()))
        .andExpect(jsonPath("$.subtotalCop").value(150000.00))
        .andExpect(jsonPath("$.discountCop").value(10000.00))
        .andExpect(jsonPath("$.totalCop").value(140000.00))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[?(@.description == 'Tratamiento pieza 16')]").exists());
  }

  @Test
  void planDeOtroTenantDevuelve404() throws Exception {
    TreatmentPlan planB = crearPlan(tenantB.getId(), patientB, new String[][]{{"100000.00", "0.00"}});

    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of("treatmentPlanId", planB.getId().toString()))))
        .andExpect(status().isNotFound());
  }

  @Test
  void planEItemsALaVezyPacienteDistintoDevuelven400() throws Exception {
    TreatmentPlan plan = crearPlan(tenantA.getId(), patientA, new String[][]{{"100000.00", "0.00"}});

    // treatmentPlanId + items manuales a la vez → ambiguo.
    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "treatmentPlanId", plan.getId().toString(),
                "patientId", patientA.getId().toString(),
                "items", List.of(itemManual("Extra", 1, "10000.00"))))))
        .andExpect(status().isBadRequest());

    // Paciente distinto al del plan → 400.
    TenantContext.setTenantId(tenantA.getId());
    Patient otro;
    try {
      otro = patientRepository.save(new Patient(tenantA.getId(), "Otro", "Paciente"));
    } finally {
      TenantContext.clear();
    }
    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "treatmentPlanId", plan.getId().toString(),
                "patientId", otro.getId().toString()))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void pagosParcialesLlevanAPagadaAutomaticamente() throws Exception {
    String facturaId = crearFactura(tokenA, Map.of(
        "patientId", patientA.getId().toString(),
        "items", List.of(itemManual("Ortodoncia", 3, "100000.00"))));

    // 100000 de 300000 → parcial.
    mockMvc.perform(post("/api/v1/invoices/" + facturaId + "/payments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("amountCop", new BigDecimal("100000.00"), "method", "transferencia"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.invoiceId").value(facturaId))
        .andExpect(jsonPath("$.invoiceStatus").value("parcial"));

    // 200000 restantes → pagada.
    mockMvc.perform(post("/api/v1/invoices/" + facturaId + "/payments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("amountCop", new BigDecimal("200000.00"), "method", "efectivo"))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.invoiceStatus").value("pagada"));
  }

  @Test
  void sobrepagoDevuelve400() throws Exception {
    String facturaId = crearFactura(tokenA, Map.of(
        "patientId", patientA.getId().toString(),
        "items", List.of(itemManual("Limpieza", 1, "80000.00"))));

    mockMvc.perform(post("/api/v1/invoices/" + facturaId + "/payments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("amountCop", new BigDecimal("80001.00"), "method", "efectivo"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  void pagoEnFacturaAjenaDevuelve404() throws Exception {
    String facturaIdB = crearFactura(tokenB, Map.of(
        "patientId", patientB.getId().toString(),
        "items", List.of(itemManual("Limpieza", 1, "80000.00"))));

    mockMvc.perform(post("/api/v1/invoices/" + facturaIdB + "/payments")
            .header("Authorization", "Bearer " + tokenA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("amountCop", new BigDecimal("80000.00"), "method", "efectivo"))))
        .andExpect(status().isNotFound());
  }

  @Test
  void rolNoAutorizadoDevuelve403() throws Exception {
    mockMvc.perform(post("/api/v1/invoices")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "patientId", patientA.getId().toString(),
                "items", List.of(itemManual("Limpieza", 1, "80000.00"))))))
        .andExpect(status().isForbidden());

    String facturaId = crearFactura(tokenA, Map.of(
        "patientId", patientA.getId().toString(),
        "items", List.of(itemManual("Limpieza", 1, "80000.00"))));
    mockMvc.perform(post("/api/v1/invoices/" + facturaId + "/payments")
            .header("Authorization", "Bearer " + tokenEspecialistaA)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("amountCop", new BigDecimal("80000.00"), "method", "efectivo"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void sinTokenDevuelve401() throws Exception {
    mockMvc.perform(post("/api/v1/invoices")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(Map.of(
                "patientId", patientA.getId().toString(),
                "items", List.of(itemManual("Limpieza", 1, "80000.00"))))))
        .andExpect(status().isUnauthorized());
  }
}

