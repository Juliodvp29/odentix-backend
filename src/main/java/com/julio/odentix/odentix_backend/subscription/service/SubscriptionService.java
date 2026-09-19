package com.julio.odentix.odentix_backend.subscription.service;

import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.exception.FeatureNotAvailableException;
import com.julio.odentix.odentix_backend.subscription.repository.PlanFeatureRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feature-gating por plan (FASE11-02).
 *
 * <p>Resuelve si el tenant activo tiene habilitado un feature según su
 * suscripción viva (trialing/active/past_due) y `plan_features`. Se invoca
 * desde `@PreAuthorize` SpEL en los controladores
 * (`@subscriptionService.requireFeature('...')`): Boot 4.1 eliminó el starter
 * AOP, así que el gating va por seguridad de métodos en vez de un aspecto
 * dedicado — misma expresividad, cero dependencias nuevas.
 *
 * <p><b>Fail-open sin suscripción viva:</b> si el tenant no tiene ninguna
 * suscripción viva se permite el acceso (con warn). Justificación: FASE11-04
 * (cobro) no existe, no hay clientes pagando, y fail-closed rompería todos los
 * tenants de tests y los actuales de prod. Se endurece a fail-closed cuando el
 * billing viva. En cambio, una clave desconocida (typo en la anotación) falla
 * cerrada (403) para que los tests la atrapen.
 */
@Service
public class SubscriptionService {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

  private static final List<SubscriptionStatus> ESTADOS_VIVOS = List.of(
      SubscriptionStatus.trialing,
      SubscriptionStatus.active,
      SubscriptionStatus.past_due);

  private final TenantSubscriptionRepository subscriptionRepository;
  private final PlanFeatureRepository planFeatureRepository;

  public SubscriptionService(
      TenantSubscriptionRepository subscriptionRepository,
      PlanFeatureRepository planFeatureRepository) {
    this.subscriptionRepository = subscriptionRepository;
    this.planFeatureRepository = planFeatureRepository;
  }

  /**
   * Verifica el feature para el tenant activo y lanza 403 si no aplica.
   *
   * <p>Devuelve `true` para usarlo dentro de `@PreAuthorize` SpEL junto a los
   * roles (ver Javadoc de la clase).
   *
   * @param featureKey clave del feature (ver `FeatureKey`).
   * @return siempre true si no lanza.
   */
  @Transactional(readOnly = true)
  public boolean requireFeature(String featureKey) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Optional<TenantSubscription> suscripcion =
        subscriptionRepository.findLiveByTenantId(tenantId, ESTADOS_VIVOS);
    if (suscripcion.isEmpty()) {
      // Pre-billing: permitir y registrar (ver Javadoc de la clase).
      log.warn("Tenant {} sin suscripción viva: acceso permitido (fail-open pre-billing).",
          tenantId);
      return true;
    }

    Plan plan = suscripcion.get().getPlan();
    boolean habilitado = planFeatureRepository
        .findByPlanIdAndFeatureKey(plan.getId(), featureKey)
        .map(f -> f.isEnabled())
        .orElse(false);
    if (!habilitado) {
      throw new FeatureNotAvailableException(featureKey, plan.getCode());
    }
    return true;
  }
}
