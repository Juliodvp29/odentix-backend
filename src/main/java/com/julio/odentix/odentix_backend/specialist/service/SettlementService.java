package com.julio.odentix.odentix_backend.specialist.service;

import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.repository.InvoiceRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.specialist.dto.CreateSettlementRequest;
import com.julio.odentix.odentix_backend.specialist.dto.SettlementResponse;
import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import com.julio.odentix.odentix_backend.specialist.entity.SpecialistSettlement;
import com.julio.odentix.odentix_backend.specialist.entity.SettlementStatus;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistRepository;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistSettlementRepository;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para liquidaciones de especialistas externos (FASE7-02).
 *
 * <p>Regla de cálculo (documentada aquí, no dispersa en el código):
 * <ul>
 *   <li>Producción bruta = suma de {@code total_cop} de las facturas <b>emitidas</b>
 *       en el periodo vinculadas a tratamientos del profesional del especialista,
 *       excluyendo facturas {@code anulada}. Las facturas sin tratamiento asociado
 *       no cuentan (solo producción trazable).</li>
 *   <li>Honorarios = producción bruta × {@code fee_percentage} / 100, redondeado
 *       a 2 decimales (HALF_UP).</li>
 *   <li>Los límites del periodo se interpretan en días calendario de la zona
 *       horaria de la clínica ({@code Tenant.timezone}, por defecto
 *       America/Bogota): {@code period_end} es inclusivo hasta las 23:59:59 local.</li>
 *   <li>Idempotencia: un mismo inicio de periodo solo se liquida una vez por
 *       especialista (segundo intento → 409). No se detectan solapamientos
 *       parciales: los periodos son responsabilidad de quien liquida.</li>
 * </ul>
 *
 * <p>La liquidación nace en estado {@code pendiente}; el pago efectivo
 * ({@code pagada} + {@code paid_at}) es una operación futura fuera de este ticket.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: un especialista de otro
 * tenant resulta invisible (404), conforme a la regla de aislamiento multi-tenant del proyecto.
 */
@Service
public class SettlementService {

  private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Bogota");

  private final SpecialistRepository specialistRepository;
  private final SpecialistSettlementRepository settlementRepository;
  private final InvoiceRepository invoiceRepository;
  private final TenantRepository tenantRepository;

  public SettlementService(
      SpecialistRepository specialistRepository,
      SpecialistSettlementRepository settlementRepository,
      InvoiceRepository invoiceRepository,
      TenantRepository tenantRepository) {
    this.specialistRepository = specialistRepository;
    this.settlementRepository = settlementRepository;
    this.invoiceRepository = invoiceRepository;
    this.tenantRepository = tenantRepository;
  }

  /**
   * Genera la liquidación de un especialista para un periodo.
   *
   * @param specialistId UUID del especialista (debe pertenecer al tenant activo).
   * @param request inicio y fin del periodo (fin inclusivo).
   * @return liquidación creada en estado {@code pendiente}.
   */
  @Transactional
  public SettlementResponse generarLiquidacion(UUID specialistId, CreateSettlementRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Specialist specialist = specialistRepository.findByIdAndTenantId(specialistId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Especialista no encontrado: " + specialistId));

    LocalDate inicio = request.getPeriodStart();
    LocalDate fin = request.getPeriodEnd();
    if (fin.isBefore(inicio)) {
      throw new IllegalArgumentException("period_end debe ser mayor o igual a period_start");
    }

    if (settlementRepository.existsBySpecialistIdAndPeriodStart(specialistId, inicio)) {
      throw new ConflictException(
          "Ya existe una liquidación para este especialista en el periodo indicado.");
    }

    ZoneId zona = zonaDeLaClinica(tenantId);
    BigDecimal bruto = invoiceRepository.sumFacturadoPorProfesionalEnPeriodo(
        tenantId,
        specialist.getProfessional().getId(),
        InvoiceStatus.anulada,
        inicio.atStartOfDay(zona).toInstant(),
        fin.plusDays(1).atStartOfDay(zona).toInstant());
    if (bruto == null) {
      bruto = BigDecimal.ZERO;
    }
    BigDecimal honorarios = bruto.multiply(specialist.getFeePercentage())
        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

    SpecialistSettlement liquidacion = new SpecialistSettlement(tenantId, specialist, inicio, fin);
    liquidacion.setGrossProductionCop(bruto);
    liquidacion.setFeeAmountCop(honorarios);
    liquidacion.setStatus(SettlementStatus.pendiente);
    liquidacion = settlementRepository.save(liquidacion);

    SettlementResponse response = new SettlementResponse();
    response.setId(liquidacion.getId());
    response.setSpecialistId(specialist.getId());
    response.setPeriodStart(inicio);
    response.setPeriodEnd(fin);
    response.setGrossProductionCop(bruto);
    response.setFeeAmountCop(honorarios);
    response.setStatus(SettlementStatus.pendiente);
    return response;
  }

  private ZoneId zonaDeLaClinica(UUID tenantId) {
    try {
      return tenantRepository.findById(tenantId)
          .map(tenant -> ZoneId.of(tenant.getTimezone()))
          .orElse(ZONA_POR_DEFECTO);
    } catch (RuntimeException e) {
      return ZONA_POR_DEFECTO;
    }
  }
}

