package com.julio.odentix.odentix_backend.billing.service;

import com.julio.odentix.odentix_backend.billing.dto.CreateInvoiceRequest;
import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentRequest;
import com.julio.odentix.odentix_backend.billing.dto.InvoiceItemInput;
import com.julio.odentix.odentix_backend.billing.dto.InvoiceItemResponse;
import com.julio.odentix.odentix_backend.billing.dto.InvoiceResponse;
import com.julio.odentix.odentix_backend.billing.dto.PaymentResponse;
import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceItem;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.entity.Payment;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceItemRepository;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanItemRepository;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para facturación simple y pagos (FASE4-04).
 *
 * <p>El tenant siempre sale del {@code TenantContext}: facturas y pagos de
 * otro tenant resultan invisibles (404). La numeración es secuencial
 * (`FAC-000001`, ...) vía secuencia de BD para no duplicar números entre
 * requests concurrentes.
 */
@Service
public class InvoiceService {

  private final InvoiceRepository invoiceRepository;
  private final InvoiceItemRepository invoiceItemRepository;
  private final PaymentRepository paymentRepository;
  private final PatientRepository patientRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final TreatmentPlanItemRepository treatmentPlanItemRepository;
  private final EntityManager entityManager;

  public InvoiceService(
      InvoiceRepository invoiceRepository,
      InvoiceItemRepository invoiceItemRepository,
      PaymentRepository paymentRepository,
      PatientRepository patientRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      TreatmentPlanItemRepository treatmentPlanItemRepository,
      EntityManager entityManager) {
    this.invoiceRepository = invoiceRepository;
    this.invoiceItemRepository = invoiceItemRepository;
    this.paymentRepository = paymentRepository;
    this.patientRepository = patientRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.treatmentPlanItemRepository = treatmentPlanItemRepository;
    this.entityManager = entityManager;
  }

  /**
   * Genera una factura manual o desde un plan de tratamiento.
   *
   * @param request paciente/plan, descuento e ítems manuales.
   * @return factura creada con sus ítems.
   */
  @Transactional
  public InvoiceResponse createInvoice(CreateInvoiceRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    boolean desdePlan = request.getTreatmentPlanId() != null;
    boolean manual = request.getItems() != null && !request.getItems().isEmpty();

    if (desdePlan && manual) {
      throw new IllegalArgumentException("Envíe treatmentPlanId o items, no ambos");
    }
    if (!desdePlan && !manual) {
      throw new IllegalArgumentException("La factura manual requiere al menos un ítem");
    }
    if (!desdePlan && request.getPatientId() == null) {
      throw new IllegalArgumentException("El paciente es obligatorio cuando no se factura desde un plan");
    }

    Patient patient;
    TreatmentPlan plan = null;
    List<Linea> lineas = new ArrayList<>();
    BigDecimal descuento;

    if (desdePlan) {
      plan = treatmentPlanRepository.findByIdAndTenantId(request.getTreatmentPlanId(), tenantId)
          .orElseThrow(() -> new ResourceNotFoundException(
              "Plan de tratamiento no encontrado: " + request.getTreatmentPlanId()));
      patient = plan.getPatient();
      if (request.getPatientId() != null && !request.getPatientId().equals(patient.getId())) {
        throw new IllegalArgumentException("El paciente no coincide con el del plan de tratamiento");
      }
      List<TreatmentPlanItem> planItems =
          treatmentPlanItemRepository.findByTreatmentPlanIdAndTenantId(plan.getId(), tenantId);
      if (planItems.isEmpty()) {
        throw new IllegalArgumentException("El plan no tiene ítems para facturar");
      }
      BigDecimal descuentoPlan = BigDecimal.ZERO;
      for (TreatmentPlanItem planItem : planItems) {
        lineas.add(new Linea(descripcionPlan(planItem), 1, planItem.getPriceCop()));
        descuentoPlan = descuentoPlan.add(planItem.getDiscountCop());
      }
      descuento = request.getDiscountCop() != null ? request.getDiscountCop() : descuentoPlan;
    } else {
      patient = patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
          .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + request.getPatientId()));
      for (InvoiceItemInput input : request.getItems()) {
        lineas.add(new Linea(input.getDescription(), input.getQuantity(), input.getUnitPriceCop()));
      }
      descuento = request.getDiscountCop() != null ? request.getDiscountCop() : BigDecimal.ZERO;
    }

    BigDecimal subtotal = lineas.stream()
        .map(linea -> linea.unitPrice.multiply(BigDecimal.valueOf(linea.quantity)))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (descuento.compareTo(subtotal) > 0) {
      throw new IllegalArgumentException("El descuento no puede superar el subtotal");
    }
    BigDecimal total = subtotal.subtract(descuento);

    Invoice invoice = new Invoice();
    invoice.setTenantId(tenantId);
    invoice.setPatient(patient);
    invoice.setTreatmentPlan(plan);
    invoice.setInvoiceNumber(siguienteNumero());
    invoice.setSubtotalCop(subtotal);
    invoice.setDiscountCop(descuento);
    invoice.setTotalCop(total);
    invoice.setIssuedAt(Instant.now());
    // Una factura de total cero no tiene nada que cobrar: nace pagada.
    invoice.setStatus(total.compareTo(BigDecimal.ZERO) == 0 ? InvoiceStatus.pagada : InvoiceStatus.pendiente);
    Invoice guardada = invoiceRepository.save(invoice);

    for (Linea linea : lineas) {
      InvoiceItem item = new InvoiceItem(tenantId, guardada, linea.description, linea.quantity, linea.unitPrice);
      invoiceItemRepository.save(item);
    }
    // Flush + clear para releer con los totales generados por la BD: sin el
    // clear, el contexto de persistencia devolvería las instancias en memoria
    // (con totalCop en null) en lugar de las filas recién calculadas.
    invoiceItemRepository.flush();
    entityManager.clear();
    Invoice releida = invoiceRepository.findByIdAndTenantId(guardada.getId(), tenantId)
        .orElseThrow(() -> new IllegalStateException("Factura recién creada no encontrada"));
    List<InvoiceItemResponse> items = invoiceItemRepository
        .findAllByTenantIdAndInvoiceId(tenantId, guardada.getId())
        .stream()
        .map(InvoiceItemResponse::fromEntity)
        .toList();

    return InvoiceResponse.fromEntity(releida, items);
  }

  /**
   * Consulta una factura por ID dentro del tenant activo, con sus ítems.
   *
   * @param invoiceId identificador de la factura.
   * @return factura con ítems.
   */
  @Transactional(readOnly = true)
  public InvoiceResponse getInvoiceById(UUID invoiceId) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada: " + invoiceId));
    return toResponse(tenantId, invoice);
  }

  /**
   * Lista facturas del tenant activo con filtros opcionales y paginación.
   *
   * @param patientId filtra por paciente (opcional).
   * @param status filtra por estado (opcional).
   * @param pageable paginación y orden.
   * @return página de facturas con sus ítems.
   */
  @Transactional(readOnly = true)
  public Page<InvoiceResponse> listInvoices(UUID patientId, InvoiceStatus status, Pageable pageable) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Page<Invoice> page;
    if (patientId != null && status != null) {
      page = invoiceRepository.findAllByTenantIdAndPatientIdAndStatus(tenantId, patientId, status, pageable);
    } else if (patientId != null) {
      page = invoiceRepository.findAllByTenantIdAndPatientId(tenantId, patientId, pageable);
    } else if (status != null) {
      page = invoiceRepository.findAllByTenantIdAndStatus(tenantId, status, pageable);
    } else {
      page = invoiceRepository.findAllByTenantId(tenantId, pageable);
    }
    return page.map(invoice -> toResponse(tenantId, invoice));
  }

  private InvoiceResponse toResponse(UUID tenantId, Invoice invoice) {
    List<InvoiceItemResponse> items = invoiceItemRepository
        .findAllByTenantIdAndInvoiceId(tenantId, invoice.getId())
        .stream()
        .map(InvoiceItemResponse::fromEntity)
        .toList();
    return InvoiceResponse.fromEntity(invoice, items);
  }

  /**
   * Registra un pago parcial o total y recalcula el estado de la factura
   * ({@code pendiente} → {@code parcial} → {@code pagada}).
   *
   * @param invoiceId factura dentro del tenant activo.
   * @param request monto, medio y referencia opcional.
   * @return pago creado con el estado recalculado de la factura.
   */
  @Transactional
  public PaymentResponse registerPayment(UUID invoiceId, CreatePaymentRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Invoice invoice = invoiceRepository.findByIdAndTenantId(invoiceId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada: " + invoiceId));
    if (invoice.getStatus() == InvoiceStatus.anulada) {
      throw new IllegalArgumentException("No se puede pagar una factura anulada");
    }

    BigDecimal pagado = paymentRepository.findAllByTenantIdAndInvoiceId(tenantId, invoiceId).stream()
        .map(Payment::getAmountCop)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal saldo = invoice.getTotalCop().subtract(pagado);
    if (request.getAmountCop().compareTo(saldo) > 0) {
      throw new IllegalArgumentException(
          "El pago de " + request.getAmountCop() + " excede el saldo pendiente de " + saldo);
    }

    Payment payment = new Payment(tenantId, invoice, request.getAmountCop(), request.getMethod());
    payment.setReference(request.getReference());
    Payment guardado = paymentRepository.save(payment);

    BigDecimal nuevoPagado = pagado.add(request.getAmountCop());
    if (nuevoPagado.compareTo(invoice.getTotalCop()) >= 0) {
      invoice.setStatus(InvoiceStatus.pagada);
    } else {
      invoice.setStatus(InvoiceStatus.parcial);
    }
    Invoice actualizada = invoiceRepository.save(invoice);

    return PaymentResponse.fromEntity(guardado, actualizada.getStatus());
  }

  /**
   * Genera una factura de un único ítem para el pago automático de una cuota (FASE6-02).
   *
   * <p>Llamado internamente por {@code PaymentPlanService.payInstallment()} para mantener
   * la trazabilidad financiera: cada cuota pagada genera su propio Invoice + Payment en BD,
   * sin requerir intervención manual del operador.
   *
   * @param tenantId tenant activo (ya validado por el caller).
   * @param patientId paciente del plan de tratamiento asociado.
   * @param descripcion texto que identifica la cuota (ej. "Cuota 2 de 3 — Plan X").
   * @param monto monto exacto de la cuota.
   * @return la factura creada (ya en estado {@code pagada}).
   */
  @Transactional
  public InvoiceResponse createInvoiceParaCuota(UUID tenantId, UUID patientId,
      String descripcion, BigDecimal monto) {
    Patient patient = patientRepository.findByIdAndTenantId(patientId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + patientId));

    Invoice invoice = new Invoice();
    invoice.setTenantId(tenantId);
    invoice.setPatient(patient);
    invoice.setInvoiceNumber(siguienteNumero());
    invoice.setSubtotalCop(monto);
    invoice.setDiscountCop(BigDecimal.ZERO);
    invoice.setTotalCop(monto);
    invoice.setIssuedAt(Instant.now());
    // El pago de la cuota crea la factura ya pagada — el pago se registra
    // a continuación en la misma transacción del caller.
    invoice.setStatus(InvoiceStatus.pagada);
    Invoice guardada = invoiceRepository.save(invoice);

    InvoiceItem item = new InvoiceItem(tenantId, guardada, descripcion, 1, monto);
    invoiceItemRepository.save(item);
    invoiceItemRepository.flush();
    entityManager.clear();

    Invoice releida = invoiceRepository.findByIdAndTenantId(guardada.getId(), tenantId)
        .orElseThrow(() -> new IllegalStateException("Factura de cuota recién creada no encontrada"));
    List<InvoiceItemResponse> items = invoiceItemRepository
        .findAllByTenantIdAndInvoiceId(tenantId, guardada.getId())
        .stream()
        .map(InvoiceItemResponse::fromEntity)
        .toList();

    return InvoiceResponse.fromEntity(releida, items);
  }

  private String siguienteNumero() {
    Long secuencia = ((Number) entityManager
        .createNativeQuery("SELECT nextval('invoice_number_seq')")
        .getSingleResult()).longValue();
    return String.format("FAC-%06d", secuencia);
  }

  private static String descripcionPlan(TreatmentPlanItem planItem) {
    if (planItem.getToothNumber() != null) {
      return "Tratamiento pieza " + planItem.getToothNumber();
    }
    return "Procedimiento del plan";
  }

  private record Linea(String description, int quantity, BigDecimal unitPrice) {
  }
}
