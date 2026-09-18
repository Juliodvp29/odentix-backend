package com.julio.odentix.odentix_backend.billing.service;

import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Job programado para marcar cuotas vencidas (FASE6-03).
 *
 * <p>Ejecuta periódicamente la función de PostgreSQL {@code mark_overdue_installments()}
 * (definida en {@code V18__create_payment_plans.sql}), pasando a estado {@code vencida}
 * todas las cuotas en estado {@code pendiente} cuya fecha de vencimiento es estrictamente
 * anterior al día actual ({@code due_date < CURRENT_DATE}).
 *
 * <p>El método {@link #execute()} puede invocarse directamente para pruebas o tareas manuales,
 * mientras que {@link #runScheduledJob()} maneja la invocación periódica con {@link Scheduled}.
 */
@Service
public class OverdueInstallmentsJob {

  private static final Logger log = LoggerFactory.getLogger(OverdueInstallmentsJob.class);

  private final InstallmentRepository installmentRepository;

  public OverdueInstallmentsJob(InstallmentRepository installmentRepository) {
    this.installmentRepository = installmentRepository;
  }

  /**
   * Ejecuta la actualización de cuotas vencidas en base de datos.
   *
   * <p>Invoca la función nativa de PostgreSQL y registra en logs el número de cuotas afectadas
   * para trazabilidad y observabilidad en producción.
   *
   * @return total de cuotas que cambiaron de {@code pendiente} a {@code vencida}.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de actualización de cuotas vencidas...");
    int afectadas = installmentRepository.markOverdueInstallments();
    log.info("Job de cuotas vencidas completado: {} cuotas marcadas como vencidas.", afectadas);
    return afectadas;
  }

  /**
   * Disparador programado diario con cron configurable.
   *
   * <p>Por defecto se ejecuta todos los días a las 02:00 AM (hora del servidor/BD).
   */
  @Scheduled(cron = "${odentix.jobs.overdue-installments.cron:0 0 2 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
