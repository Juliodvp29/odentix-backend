package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: tratamiento sin seguimiento → oportunidad de negocio (FASE9-01).
 *
 * <p>Condición: plan de tratamiento en estado {@code presentado} o {@code en_decision}
 * cuya última interacción conocida ({@code lastContactAt}, o {@code presentedAt}, o
 * {@code createdAt} como fallback) es anterior al umbral configurable (por defecto 3
 * días).
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) de tipo {@code tratamiento_sin_seguimiento} para el mismo plan,
 * no se crea otra — correr el job dos veces seguidas no duplica.
 *
 * <p>Prioridad determinística (1 a 5, escala de schema.sql):
 * <ul>
 *   <li>Valor ≥ $2.000.000 COP → prioridad 4 (Alta).</li>
 *   <li>Valor ≥ $500.000 COP → prioridad 3 (Media-alta).</li>
 *   <li>Valor menor → prioridad 2 (Media).</li>
 * </ul>
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas, igual que los jobs de FASE6-03 y
 * FASE8-02. Cada oportunidad hereda el {@code tenantId} de su plan de tratamiento, así
 * que el aislamiento se mantiene dato por dato.
 */
@Service
public class TreatmentPlanFollowupJob {

  private static final Logger log = LoggerFactory.getLogger(TreatmentPlanFollowupJob.class);

  /** Estados del plan que indican que el paciente aún no ha decidido. */
  static final List<TreatmentPlanStatus> ESTADOS_CANDIDATOS =
      List.of(TreatmentPlanStatus.presentado, TreatmentPlanStatus.en_decision);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  /** Umbral de corte para prioridad alta: valor ≥ $2.000.000 COP. */
  private static final BigDecimal UMBRAL_ALTA = new BigDecimal("2000000.00");

  /** Umbral de corte para prioridad media-alta: valor ≥ $500.000 COP. */
  private static final BigDecimal UMBRAL_MEDIA_ALTA = new BigDecimal("500000.00");

  private final TreatmentPlanRepository treatmentPlanRepository;
  private final OpportunityRepository opportunityRepository;

  /** Número de días sin contacto para considerar un plan "sin seguimiento". */
  private final int followupDays;

  public TreatmentPlanFollowupJob(
      TreatmentPlanRepository treatmentPlanRepository,
      OpportunityRepository opportunityRepository,
      @Value("${odentix.opportunities.treatment-followup-days:3}") int followupDays) {
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.opportunityRepository = opportunityRepository;
    this.followupDays = followupDays;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de tratamientos sin seguimiento (umbral: {} días)...", followupDays);

    Instant cutoff = Instant.now().minus(Duration.ofDays(followupDays));
    List<TreatmentPlan> candidatos =
        treatmentPlanRepository.findFollowupCandidates(ESTADOS_CANDIDATOS, cutoff);

    int creadas = 0;
    for (TreatmentPlan plan : candidatos) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "treatment_plan", plan.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      short prioridad = calcularPrioridad(plan.getTotalPriceCop());

      Opportunity oportunidad = new Opportunity(
          plan.getTenantId(),
          OpportunityType.tratamiento_sin_seguimiento,
          prioridad);
      oportunidad.setRelatedEntityType("treatment_plan");
      oportunidad.setRelatedEntityId(plan.getId());
      oportunidad.setEstimatedValueCop(plan.getTotalPriceCop());

      opportunityRepository.save(oportunidad);
      creadas++;
    }

    log.info("Job de tratamientos sin seguimiento completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Calcula la prioridad (1–5) de la oportunidad basándose en el valor del tratamiento.
   *
   * <p>Criterio determinístico sin lógica de IA: el valor monetario del plan define
   * cuánta atención merece esta oportunidad. Valores más altos = más urgente actuar.
   */
  static short calcularPrioridad(BigDecimal totalPriceCop) {
    if (totalPriceCop == null) {
      return 2;
    }
    if (totalPriceCop.compareTo(UMBRAL_ALTA) >= 0) {
      return 4;
    }
    if (totalPriceCop.compareTo(UMBRAL_MEDIA_ALTA) >= 0) {
      return 3;
    }
    return 2;
  }

  /**
   * Disparador programado diario con cron configurable.
   *
   * <p>Por defecto se ejecuta todos los días a las 08:00 AM (hora del servidor/BD),
   * horario en que la clínica abre y el personal puede actuar sobre las oportunidades.
   */
  @Scheduled(cron = "${odentix.jobs.treatment-followup-opportunities.cron:0 0 8 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
