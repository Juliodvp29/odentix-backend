package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: cita de alto riesgo → oportunidad de negocio (FASE9-02).
 *
 * <p>Condición: cita en estado {@code programada} con {@code risk_level = alto}
 * que inicia dentro de las próximas 48 horas (alto riesgo de no-show y pérdida
 * del espacio).
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para la misma cita, no se crea otra.
 *
 * <p>Prioridad fija 4 (Alta): una cita de alto riesgo próxima siempre merece
 * acción (confirmar, reagendar o preparar el espacio).
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas. Cada oportunidad hereda el
 * {@code tenantId} de su cita.
 */
@Service
public class HighRiskAppointmentJob {

  private static final Logger log = LoggerFactory.getLogger(HighRiskAppointmentJob.class);

  /** Horizonte de anticipación para considerar una cita "próxima". */
  static final Duration HORIZONTE = Duration.ofHours(48);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  private final AppointmentRepository appointmentRepository;
  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionFactory actionFactory;

  public HighRiskAppointmentJob(
      AppointmentRepository appointmentRepository,
      OpportunityRepository opportunityRepository,
      OpportunityActionFactory actionFactory) {
    this.appointmentRepository = appointmentRepository;
    this.opportunityRepository = opportunityRepository;
    this.actionFactory = actionFactory;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de citas de alto riesgo...");

    Instant ahora = Instant.now();
    List<Appointment> candidatas = appointmentRepository
        .findByStatusAndRiskLevelAndStartsAtBetween(
            AppointmentStatus.programada, RiskLevel.alto, ahora, ahora.plus(HORIZONTE));

    int creadas = 0;
    for (Appointment cita : candidatas) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "appointment", cita.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      BigDecimal valor = cita.getEstimatedValueCop() != null
          ? cita.getEstimatedValueCop()
          : BigDecimal.ZERO;

      Opportunity oportunidad = new Opportunity(
          cita.getTenantId(),
          OpportunityType.cita_alto_riesgo,
          (short) 4);
      oportunidad.setRelatedEntityType("appointment");
      oportunidad.setRelatedEntityId(cita.getId());
      oportunidad.setEstimatedValueCop(valor);

      opportunityRepository.save(oportunidad);
      // FASE9-03: cada oportunidad trae sus acciones sugeridas.
      actionFactory.paraAppointment(oportunidad, cita);
      creadas++;
    }

    log.info("Job de citas de alto riesgo completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Disparador programado diario a primera hora con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.high-risk-opportunities.cron:0 0 7 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
