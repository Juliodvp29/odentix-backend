package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: saldo vencido → oportunidad de negocio (FASE9-02).
 *
 * <p>Condición: cuota en estado {@code vencida} (marcada por el job diario de
 * FASE6-03; este job corre después, a las 02:30). Cada cuota vencida es cartera
 * por recuperar.
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para la misma cuota, no se crea otra.
 *
 * <p>Prioridad determinística (1 a 5) por monto en mora:
 * <ul>
 *   <li>Mora ≥ $500.000 COP → prioridad 4 (Alta).</li>
 *   <li>Mora menor → prioridad 3 (Media-alta).</li>
 * </ul>
 * El valor estimado es el monto total de la cuota vencida.
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas. Cada oportunidad hereda el
 * {@code tenantId} de su cuota.
 */
@Service
public class OverdueInstallmentOpportunityJob {

  private static final Logger log = LoggerFactory.getLogger(OverdueInstallmentOpportunityJob.class);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  /** Umbral de corte para prioridad alta: mora ≥ $500.000 COP. */
  private static final BigDecimal UMBRAL_ALTA = new BigDecimal("500000.00");

  private final InstallmentRepository installmentRepository;
  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionFactory actionFactory;
  private final PaymentPlanRepository paymentPlanRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;

  public OverdueInstallmentOpportunityJob(
      InstallmentRepository installmentRepository,
      OpportunityRepository opportunityRepository,
      OpportunityActionFactory actionFactory,
      PaymentPlanRepository paymentPlanRepository,
      TreatmentPlanRepository treatmentPlanRepository) {
    this.installmentRepository = installmentRepository;
    this.opportunityRepository = opportunityRepository;
    this.actionFactory = actionFactory;
    this.paymentPlanRepository = paymentPlanRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de saldos vencidos...");

    List<Installment> candidatas = installmentRepository.findByStatus(InstallmentStatus.vencida);

    int creadas = 0;
    for (Installment cuota : candidatas) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "installment", cuota.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      BigDecimal monto = cuota.getAmountCop() != null
          ? cuota.getAmountCop()
          : BigDecimal.ZERO;
      short prioridad = monto.compareTo(UMBRAL_ALTA) >= 0 ? (short) 4 : (short) 3;

      Opportunity oportunidad = new Opportunity(
          cuota.getTenantId(),
          OpportunityType.saldo_vencido,
          prioridad);
      oportunidad.setRelatedEntityType("installment");
      oportunidad.setRelatedEntityId(cuota.getId());
      oportunidad.setEstimatedValueCop(monto);

      opportunityRepository.save(oportunidad);
      // FASE9-03: cada oportunidad trae sus acciones sugeridas (paciente
      // resuelto por la cadena cuota → plan de pago → tratamiento).
      actionFactory.paraInstallment(oportunidad, cuota, resolverPaciente(cuota));
      creadas++;
    }

    log.info("Job de saldos vencidos completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Resuelve el paciente de la cuota para la acción sugerida. Si la cadena se
   * rompió (plan o tratamiento borrados), devuelve null y la sugerencia queda
   * solo con tarea.
   */
  private Patient resolverPaciente(Installment cuota) {
    if (cuota.getPaymentPlan() == null) {
      return null;
    }
    return paymentPlanRepository.findById(cuota.getPaymentPlan().getId())
        .map(plan -> treatmentPlanRepository.findById(plan.getTreatmentPlanId())
            .map(t -> t.getPatient())
            .orElse(null))
        .orElse(null);
  }

  /**
   * Disparador programado diario con cron configurable.
   *
   * <p>Por defecto a las 02:30 AM: después del job de FASE6-03 (02:00 AM) que
   * marca las cuotas vencidas, para detectarlas en la misma madrugada.
   */
  @Scheduled(cron = "${odentix.jobs.overdue-opportunities.cron:0 30 2 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
