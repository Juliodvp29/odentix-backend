package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
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
 * Regla automática: lead sin respuesta → oportunidad de negocio (FASE9-02).
 *
 * <p>Condición: lead en estado {@code nuevo} sin contacto desde el umbral
 * configurable (por defecto 24 horas; usa `lastContactAt`, o `createdAt` si
 * nunca hubo contacto). Usa el índice `idx_leads_unresponded`.
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para el mismo lead, no se crea otra.
 *
 * <p>Prioridad determinística (1 a 5):
 * <ul>
 *   <li>Con valor estimado o fuente registrada → prioridad 4 (Alta).</li>
 *   <li>Sin datos comerciales → prioridad 3 (Media-alta).</li>
 * </ul>
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas. Cada oportunidad hereda el
 * {@code tenantId} de su lead.
 */
@Service
public class LeadUnrespondedJob {

  private static final Logger log = LoggerFactory.getLogger(LeadUnrespondedJob.class);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  private final LeadRepository leadRepository;
  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionFactory actionFactory;

  /** Horas sin respuesta para considerar un lead "sin respuesta". */
  private final int unrespondedHours;

  public LeadUnrespondedJob(
      LeadRepository leadRepository,
      OpportunityRepository opportunityRepository,
      OpportunityActionFactory actionFactory,
      @Value("${odentix.opportunities.lead-unresponded-hours:24}") int unrespondedHours) {
    this.leadRepository = leadRepository;
    this.opportunityRepository = opportunityRepository;
    this.actionFactory = actionFactory;
    this.unrespondedHours = unrespondedHours;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de leads sin respuesta (umbral: {} horas)...", unrespondedHours);

    Instant cutoff = Instant.now().minus(Duration.ofHours(unrespondedHours));
    List<Lead> candidatos = leadRepository.findUnresponded(LeadStatus.nuevo, cutoff);

    int creadas = 0;
    for (Lead lead : candidatos) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "lead", lead.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      BigDecimal valor = lead.getEstimatedValueCop() != null
          ? lead.getEstimatedValueCop()
          : BigDecimal.ZERO;
      short prioridad = (valor.compareTo(BigDecimal.ZERO) > 0
          || (lead.getSource() != null && !lead.getSource().isBlank()))
          ? (short) 4
          : (short) 3;

      Opportunity oportunidad = new Opportunity(
          lead.getTenantId(),
          OpportunityType.lead_sin_respuesta,
          prioridad);
      oportunidad.setRelatedEntityType("lead");
      oportunidad.setRelatedEntityId(lead.getId());
      oportunidad.setEstimatedValueCop(valor);

      opportunityRepository.save(oportunidad);
      // FASE9-03: cada oportunidad trae sus acciones sugeridas.
      actionFactory.paraLead(oportunidad, lead);
      creadas++;
    }

    log.info("Job de leads sin respuesta completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Disparador programado cada 2 horas con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.lead-unresponded-opportunities.cron:0 0 */2 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
