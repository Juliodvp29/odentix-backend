package com.julio.odentix.odentix_backend.saas.service;

import com.julio.odentix.odentix_backend.saas.client.BoldClient;
import com.julio.odentix.odentix_backend.saas.entity.SaasPayment;
import com.julio.odentix.odentix_backend.saas.entity.SaasPaymentStatus;
import com.julio.odentix.odentix_backend.saas.repository.SaasPaymentRepository;
import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
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
 * Renovación y mora de suscripciones del SaaS (FASE11-04).
 *
 * <p>Dos responsabilidades, una corrida diaria:
 * <ul>
 *   <li><b>Renovación:</b> suscripción viva cuyo periodo vence en ≤3 días y sin
 *       link pendiente → crea el link de Bold del ciclo (la clínica lo paga;
 *       el webhook extiende al aprobar).</li>
 *   <li><b>Mora:</b> `past_due` con periodo vencido más allá de la gracia
 *       ({@value #DIAS_GRACIA} días) → `cancelled` (pierde features y topes
 *       por el gating de FASE11-02/03).</li>
 * </ul>
 *
 * <p>Corre en contexto de sistema (sin request): cubre todos los tenants.
 */
@Service
public class SaasRenewalJob {

  private static final Logger log = LoggerFactory.getLogger(SaasRenewalJob.class);

  /** Días de gracia tras vencer el periodo en mora antes de cancelar. */
  static final int DIAS_GRACIA = 7;

  /** Anticipación para generar el link de renovación. */
  static final Duration ANTICIPACION_RENOVACION = Duration.ofDays(3);

  private static final List<SubscriptionStatus> ESTADOS_VIVOS = List.of(
      SubscriptionStatus.trialing,
      SubscriptionStatus.active,
      SubscriptionStatus.past_due);

  private final TenantSubscriptionRepository subscriptionRepository;
  private final SaasPaymentRepository paymentRepository;
  private final BoldClient boldClient;

  public SaasRenewalJob(
      TenantSubscriptionRepository subscriptionRepository,
      SaasPaymentRepository paymentRepository,
      BoldClient boldClient) {
    this.subscriptionRepository = subscriptionRepository;
    this.paymentRepository = paymentRepository;
    this.boldClient = boldClient;
  }

  /**
   * Ejecuta renovación y mora.
   *
   * @return array {renovaciones creadas, cancelaciones}.
   */
  @Transactional
  public int[] execute() {
    Instant ahora = Instant.now();
    int renovaciones = 0;
    int cancelaciones = 0;

    for (TenantSubscription sub : subscriptionRepository.findByStatusIn(ESTADOS_VIVOS)) {
      if (sub.getStatus() == SubscriptionStatus.past_due
          && sub.getCurrentPeriodEnd().plus(Duration.ofDays(DIAS_GRACIA)).isBefore(ahora)) {
        sub.setStatus(SubscriptionStatus.cancelled);
        subscriptionRepository.save(sub);
        cancelaciones++;
        log.info("Suscripción {} cancelada por mora (gracia de {} días).",
            sub.getId(), DIAS_GRACIA);
        continue;
      }

      if (sub.getCurrentPeriodEnd().minus(ANTICIPACION_RENOVACION).isBefore(ahora)
          && paymentRepository
              .findBySubscriptionIdAndStatus(sub.getId(), SaasPaymentStatus.pendiente)
              .isEmpty()) {
        BigDecimal monto = sub.getBillingCycle() == BillingCycle.annual
            ? sub.getPlan().getAnnualPriceCop()
            : sub.getPlan().getMonthlyPriceCop();
        String referencia = "ODX-" + sub.getTenantId().toString().replace("-", "").substring(0, 8)
            + "-" + System.currentTimeMillis();
        SaasPayment pago = new SaasPayment(
            sub.getTenantId(), sub, referencia, monto, sub.getBillingCycle());
        pago = paymentRepository.save(pago);
        BoldClient.PaymentLink link = boldClient.createPaymentLink(
            monto.longValue(),
            referencia,
            "Renovación Odentix (" + sub.getPlan().getCode() + ")",
            null,
            sub.getCurrentPeriodEnd().plus(Duration.ofDays(DIAS_GRACIA)).toEpochMilli()
                * 1_000_000L);
        pago.setBoldPaymentLink(link.url());
        renovaciones++;
        log.info("Link de renovación creado para la suscripción {}.", sub.getId());
      }
    }

    log.info("Job de renovación SaaS completado: {} renovaciones, {} cancelaciones.",
        renovaciones, cancelaciones);
    return new int[]{renovaciones, cancelaciones};
  }

  /**
   * Disparador programado diario con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.saas-renewal.cron:0 0 3 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
