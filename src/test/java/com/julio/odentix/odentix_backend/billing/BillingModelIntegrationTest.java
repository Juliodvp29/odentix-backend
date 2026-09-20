package com.julio.odentix.odentix_backend.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceItem;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.entity.Payment;
import com.julio.odentix.odentix_backend.billing.entity.PaymentMethod;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceItemRepository;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para el modelo de facturación (FASE4-03) contra
 * PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket: se persiste una factura con ítems y su total
 * coincide con la suma de los ítems, más el aislamiento cross-tenant
 * verificado con test.
 */
class BillingModelIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private InvoiceRepository invoiceRepository;

  @Autowired
  private InvoiceItemRepository invoiceItemRepository;

  @Autowired
  private PaymentRepository paymentRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TreatmentPlanRepository treatmentPlanRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Patient patientA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Facturación Alfa", "911111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Facturación Beta", "912333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      patientA = patientRepository.save(new Patient(tenantA.getId(), "Ana", "Torres"));
    } finally {
      TenantContext.clear();
    }
  }

  private Invoice nuevaFactura(UUID tenantId, Patient patient, String numero,
      BigDecimal subtotal, BigDecimal descuento, BigDecimal total) {
    Invoice invoice = new Invoice();
    invoice.setTenantId(tenantId);
    invoice.setPatient(patient);
    invoice.setInvoiceNumber(numero);
    invoice.setStatus(InvoiceStatus.pendiente);
    invoice.setSubtotalCop(subtotal);
    invoice.setDiscountCop(descuento);
    invoice.setTotalCop(total);
    invoice.setIssuedAt(Instant.now());
    return invoice;
  }

  private Invoice guardarFacturaComo(UUID tenantId, Invoice invoice) {
    TenantContext.setTenantId(tenantId);
    try {
      return invoiceRepository.save(invoice);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void facturaConItemsYTotalCoincideConSuma() {
    // 2 × 150000 + 1 × 200000 = 500000 de subtotal, 50000 de descuento, 450000 total.
    Invoice guardada = guardarFacturaComo(tenantA.getId(), nuevaFactura(
        tenantA.getId(), patientA, "FAC-" + UUID.randomUUID(),
        new BigDecimal("500000.00"), new BigDecimal("50000.00"), new BigDecimal("450000.00")));

    TenantContext.setTenantId(tenantA.getId());
    try {
      invoiceItemRepository.save(new InvoiceItem(
          tenantA.getId(), guardada, "Obturación resina", 2, new BigDecimal("150000.00")));
      invoiceItemRepository.save(new InvoiceItem(
          tenantA.getId(), guardada, "Endodoncia", 1, new BigDecimal("200000.00")));
    } finally {
      TenantContext.clear();
    }

    // Releer de BD para incluir la columna generada total_cop de cada ítem.
    TenantContext.setTenantId(tenantA.getId());
    try {
      List<InvoiceItem> items =
          invoiceItemRepository.findAllByTenantIdAndInvoiceId(tenantA.getId(), guardada.getId());
      assertThat(items).hasSize(2);

      BigDecimal sumaItems = items.stream()
          .map(item -> {
            assertThat(item.getTotalCop()).isNotNull();
            assertThat(item.getTotalCop())
                .isEqualByComparingTo(item.getUnitPriceCop().multiply(BigDecimal.valueOf(item.getQuantity())));
            return item.getTotalCop();
          })
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      Invoice releida = invoiceRepository
          .findByIdAndTenantId(guardada.getId(), tenantA.getId())
          .orElseThrow();
      assertThat(releida.getSubtotalCop()).isEqualByComparingTo(sumaItems);
      assertThat(releida.getTotalCop())
          .isEqualByComparingTo(releida.getSubtotalCop().subtract(releida.getDiscountCop()));
      assertThat(releida.getStatus()).isEqualTo(InvoiceStatus.pendiente);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void facturaPuedeAsociarseAPlanYPago() {
    TenantContext.setTenantId(tenantA.getId());
    TreatmentPlan plan;
    try {
      plan = treatmentPlanRepository.save(new TreatmentPlan(tenantA.getId(), patientA));
    } finally {
      TenantContext.clear();
    }

    Invoice factura = nuevaFactura(
        tenantA.getId(), patientA, "FAC-" + UUID.randomUUID(),
        new BigDecimal("300000.00"), BigDecimal.ZERO, new BigDecimal("300000.00"));
    factura.setTreatmentPlan(plan);
    Invoice guardada = guardarFacturaComo(tenantA.getId(), factura);

    TenantContext.setTenantId(tenantA.getId());
    try {
      Payment pago = new Payment(
          tenantA.getId(), guardada, new BigDecimal("100000.00"), PaymentMethod.transferencia);
      pago.setReference("TX-123");
      Payment guardado = paymentRepository.save(pago);

      assertThat(guardado.getId()).isNotNull();
      assertThat(guardado.getMethod()).isEqualTo(PaymentMethod.transferencia);
      assertThat(guardado.getPaidAt()).isNotNull();

      List<Payment> pagos =
          paymentRepository.findAllByTenantIdAndInvoiceId(tenantA.getId(), guardada.getId());
      assertThat(pagos).hasSize(1);

      Invoice releida = invoiceRepository
          .findByIdAndTenantId(guardada.getId(), tenantA.getId())
          .orElseThrow();
      assertThat(releida.getTreatmentPlan().getId()).isEqualTo(plan.getId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void numeroDuplicadoEnElTenantViolaConstraint() {
    String numero = "FAC-DUP-" + UUID.randomUUID();
    guardarFacturaComo(tenantA.getId(), nuevaFactura(
        tenantA.getId(), patientA, numero, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(() -> invoiceRepository.saveAndFlush(nuevaFactura(
          tenantA.getId(), patientA, numero, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenant() {
    Invoice facturaA = guardarFacturaComo(tenantA.getId(), nuevaFactura(
        tenantA.getId(), patientA, "FAC-" + UUID.randomUUID(),
        new BigDecimal("100000.00"), BigDecimal.ZERO, new BigDecimal("100000.00")));

    // Query ingenua desde otro tenant: la factura ajena es invisible.
    TenantContext.setTenantId(tenantB.getId());
    try {
      assertThat(invoiceRepository.findAll()).isEmpty();
      assertThat(invoiceRepository.findById(facturaA.getId())).isEmpty();
    } finally {
      TenantContext.clear();
    }

    assertThat(invoiceRepository.findByIdAndTenantId(facturaA.getId(), tenantB.getId())).isEmpty();

    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThat(invoiceRepository.findByIdAndTenantId(facturaA.getId(), tenantA.getId())).isPresent();
    } finally {
      TenantContext.clear();
    }
  }
}

