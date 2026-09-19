package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: paciente inactivo → oportunidad de negocio (FASE9-02).
 *
 * <p>Condición: paciente activo ({@code is_active = true}) sin citas ni planes
 * de tratamiento desde el umbral configurable (por defecto 6 meses). Es
 * recuperación preventiva: invitar a control antes de perder al paciente.
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para el mismo paciente, no se crea otra.
 *
 * <p>Prioridad fija 2 (Media): es trabajo preventivo de largo plazo, no una
 * pérdida inminente. Valor estimado en cero (no hay monto asociado directo).
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas. Cada oportunidad hereda el
 * {@code tenantId} de su paciente.
 */
@Service
public class InactivePatientJob {

  private static final Logger log = LoggerFactory.getLogger(InactivePatientJob.class);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  private final PatientRepository patientRepository;
  private final OpportunityRepository opportunityRepository;

  /** Meses sin interacción para considerar un paciente "inactivo". */
  private final int inactiveMonths;

  public InactivePatientJob(
      PatientRepository patientRepository,
      OpportunityRepository opportunityRepository,
      @Value("${odentix.opportunities.inactive-patient-months:6}") int inactiveMonths) {
    this.patientRepository = patientRepository;
    this.opportunityRepository = opportunityRepository;
    this.inactiveMonths = inactiveMonths;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de pacientes inactivos (umbral: {} meses)...", inactiveMonths);

    // Instant no soporta MONTHS: se calcula con OffsetDateTime (meses calendario).
    Instant cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(inactiveMonths).toInstant();
    List<Patient> candidatos = patientRepository.findInactiveSince(cutoff);

    int creadas = 0;
    for (Patient paciente : candidatos) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "patient", paciente.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      Opportunity oportunidad = new Opportunity(
          paciente.getTenantId(),
          OpportunityType.paciente_inactivo,
          (short) 2);
      oportunidad.setRelatedEntityType("patient");
      oportunidad.setRelatedEntityId(paciente.getId());
      oportunidad.setEstimatedValueCop(BigDecimal.ZERO);

      opportunityRepository.save(oportunidad);
      creadas++;
    }

    log.info("Job de pacientes inactivos completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Disparador programado diario con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.inactive-patient-opportunities.cron:0 0 6 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
