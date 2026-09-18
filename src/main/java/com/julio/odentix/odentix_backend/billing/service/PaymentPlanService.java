package com.julio.odentix.odentix_backend.billing.service;

import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentPlanRequest;
import com.julio.odentix.odentix_backend.billing.dto.InstallmentResponse;
import com.julio.odentix.odentix_backend.billing.dto.PaymentPlanResponse;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.entity.PaymentPlan;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para planes de pago en cuotas (FASE6-02).
 *
 * <p>Dos operaciones principales:
 * <ul>
 *   <li>Crear un plan de N cuotas mensuales para un plan de tratamiento.</li>
 *   <li>Marcar una cuota como pagada, generando la factura e Invoice correspondientes
 *       vía {@link InvoiceService} para mantener la trazabilidad financiera completa.</li>
 * </ul>
 *
 * <p>El tenant siempre sale del {@code TenantContext}: cualquier recurso de otro
 * tenant resulta invisible (404), conforme a la regla §5 de AGENTS.md.
 */
@Service
public class PaymentPlanService {

  private final PaymentPlanRepository paymentPlanRepository;
  private final InstallmentRepository installmentRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final InvoiceService invoiceService;

  public PaymentPlanService(
      PaymentPlanRepository paymentPlanRepository,
      InstallmentRepository installmentRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      InvoiceService invoiceService) {
    this.paymentPlanRepository = paymentPlanRepository;
    this.installmentRepository = installmentRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.invoiceService = invoiceService;
  }

  /**
   * Crea un plan de pago en N cuotas mensuales para un plan de tratamiento.
   *
   * <p>Regla de distribución: cada cuota recibe {@code totalAmountCop / N} redondeado
   * a 2 decimales (HALF_UP). El resto de redondeo se absorbe en la última cuota
   * para garantizar que la suma de cuotas sea exactamente igual a {@code totalAmountCop}.
   * Esto sigue la convención bancaria estándar y se documenta aquí para no dispersar
   * la regla de negocio en comentarios inline.
   *
   * <p>Política de un plan activo por tratamiento: si el tratamiento ya tiene un plan
   * de pago, se lanza {@link IllegalStateException} con HTTP 409. El modelo permite
   * múltiples planes para renegociaciones futuras, pero el endpoint lo previene para
   * evitar confusión operativa.
   *
   * @param treatmentPlanId UUID del plan de tratamiento (debe pertenecer al tenant activo).
   * @param request monto total y número de cuotas.
   * @return plan de pago creado con todas sus cuotas.
   */
  @Transactional
  public PaymentPlanResponse createPaymentPlan(UUID treatmentPlanId, CreatePaymentPlanRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    // Verificar que el tratamiento existe y pertenece al tenant activo.
    TreatmentPlan tratamiento = treatmentPlanRepository.findByIdAndTenantId(treatmentPlanId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Plan de tratamiento no encontrado: " + treatmentPlanId));

    // Política: un tratamiento solo debe tener un plan de pago activo a la vez.
    List<PaymentPlan> existentes = paymentPlanRepository.findByTreatmentPlanId(treatmentPlanId);
    if (!existentes.isEmpty()) {
      throw new ConflictException(
          "El plan de tratamiento ya tiene un plan de pago activo. "
              + "Elimínelo antes de crear uno nuevo.");
    }

    int n = request.getInstallmentsCount();
    BigDecimal total = request.getTotalAmountCop();

    // Calcular monto base por cuota (truncado a 2 decimales) y el resto que va a la última.
    // Ejemplo: 1000 / 3 = 333.33 × 3 = 999.99 → última cuota = 333.33 + 0.01 = 333.34.
    BigDecimal montoCuota = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
    BigDecimal sumaBase = montoCuota.multiply(BigDecimal.valueOf(n - 1));
    BigDecimal montoUltimaCuota = total.subtract(sumaBase);

    PaymentPlan plan = new PaymentPlan();
    plan.setTenantId(tenantId);
    plan.setTreatmentPlanId(treatmentPlanId);
    plan.setTotalAmountCop(total);
    plan.setInstallmentsCount(n);

    LocalDate hoy = LocalDate.now();
    for (int i = 1; i <= n; i++) {
      Installment cuota = new Installment();
      cuota.setTenantId(tenantId);
      cuota.setInstallmentNumber(i);
      cuota.setAmountCop(i == n ? montoUltimaCuota : montoCuota);
      // Vencimiento mensual: cuota 1 vence en 1 mes, cuota 2 en 2 meses, etc.
      cuota.setDueDate(hoy.plusMonths(i));
      plan.addInstallment(cuota);
    }

    PaymentPlan guardado = paymentPlanRepository.save(plan);

    List<Installment> cuotas = installmentRepository
        .findByPaymentPlanIdOrderByInstallmentNumberAsc(guardado.getId());
    List<InstallmentResponse> cuotasDto = cuotas.stream()
        .map(InstallmentResponse::fromEntity)
        .toList();

    return PaymentPlanResponse.fromEntity(guardado, cuotasDto);
  }

  /**
   * Marca una cuota como pagada y genera la factura + pago correspondientes.
   *
   * <p>El pago genera un {@code Invoice} automático (paciente del tratamiento,
   * ítem = "Cuota N de N — Plan de pago [UUID]") y un {@code Payment} contra ella.
   * Esto mantiene la trazabilidad financiera completa sin requerir que el operador
   * cree la factura manualmente.
   *
   * <p>La cuota solo puede pagarse si está en estado {@code pendiente} o {@code vencida}.
   * Intentar pagar una cuota ya pagada lanza {@link IllegalStateException} con HTTP 409.
   *
   * @param installmentId UUID de la cuota (debe pertenecer al tenant activo).
   * @return estado actualizado de la cuota.
   */
  @Transactional
  public InstallmentResponse payInstallment(UUID installmentId) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Installment cuota = installmentRepository.findById(installmentId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Cuota no encontrada: " + installmentId));

    if (cuota.getStatus() == InstallmentStatus.pagada) {
      throw new ConflictException("La cuota ya fue pagada.");
    }

    // Resolver el tratamiento para obtener el paciente (necesario para el Invoice).
    PaymentPlan planPago = cuota.getPaymentPlan();
    TreatmentPlan tratamiento = treatmentPlanRepository
        .findByIdAndTenantId(planPago.getTreatmentPlanId(), tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Plan de tratamiento del plan de pago no encontrado."));

    // Generar Invoice + Payment automáticos para la cuota.
    // La descripción identifica inequívocamente la cuota dentro del plan.
    String descripcion = String.format("Cuota %d de %d — Plan de pago %s",
        cuota.getInstallmentNumber(),
        planPago.getInstallmentsCount(),
        planPago.getId());

    invoiceService.createInvoiceParaCuota(
        tenantId,
        tratamiento.getPatient().getId(),
        descripcion,
        cuota.getAmountCop());

    // Marcar la cuota como pagada.
    cuota.setStatus(InstallmentStatus.pagada);
    cuota.setPaidAt(Instant.now());
    Installment actualizada = installmentRepository.save(cuota);

    return InstallmentResponse.fromEntity(actualizada);
  }
}
