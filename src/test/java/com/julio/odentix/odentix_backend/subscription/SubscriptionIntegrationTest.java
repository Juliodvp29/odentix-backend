package com.julio.odentix.odentix_backend.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.FeatureKey;
import com.julio.odentix.odentix_backend.subscription.entity.LimitKey;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.PlanFeature;
import com.julio.odentix.odentix_backend.subscription.entity.PlanLimit;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.PlanFeatureRepository;
import com.julio.odentix.odentix_backend.subscription.repository.PlanLimitRepository;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para planes y suscripciones (FASE11-01) contra
 * PostgreSQL 16 real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Seeds de V25: 3 planes con precios, 8 features y 6 límites cada uno.</li>
 *   <li>Lectura del plan activo, features y límites de un tenant dado.</li>
 *   <li>Restricción de una sola suscripción viva por tenant.</li>
 *   <li>Aislamiento cross-tenant estricto (regla de aislamiento multi-tenant del proyecto).</li>
 * </ul>
 */
class SubscriptionIntegrationTest extends AbstractIntegrationTest {

  private static final List<SubscriptionStatus> LIVE_STATUSES =
      List.of(SubscriptionStatus.trialing, SubscriptionStatus.active, SubscriptionStatus.past_due);

  @Autowired
  private PlanRepository planRepository;

  @Autowired
  private PlanFeatureRepository planFeatureRepository;

  @Autowired
  private PlanLimitRepository planLimitRepository;

  @Autowired
  private TenantSubscriptionRepository tenantSubscriptionRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Suscripciones Alfa", "903456789-3"));
    tenantB = tenantRepository.save(new Tenant("Clínica Suscripciones Beta", "904567890-4"));
  }

  @Test
  void planesFeaturesYLimitesSembradosPorMigracion() {
    List<Plan> activos = planRepository.findByActiveTrue();
    assertThat(activos).extracting(Plan::getCode)
        .containsExactlyInAnyOrder("esencial", "profesional", "clinica");

    Plan esencial = planRepository.findByCode("esencial").orElseThrow();
    assertThat(esencial.getMonthlyPriceCop()).isEqualByComparingTo("99900.00");
    assertThat(esencial.getAnnualPriceCop()).isEqualByComparingTo("999000.00");

    Plan profesional = planRepository.findByCode("profesional").orElseThrow();
    List<PlanFeature> featuresProfesional = planFeatureRepository.findByPlanId(profesional.getId());
    assertThat(featuresProfesional).hasSize(8);
    assertThat(featureEnabled(featuresProfesional, FeatureKey.CRM_LEADS)).isTrue();
    assertThat(featureEnabled(featuresProfesional, FeatureKey.AI_ASSISTANT)).isFalse();
    assertThat(featureEnabled(featuresProfesional, FeatureKey.INVENTORY_ALERTS)).isFalse();

    Plan clinica = planRepository.findByCode("clinica").orElseThrow();
    List<PlanFeature> featuresClinica = planFeatureRepository.findByPlanId(clinica.getId());
    assertThat(featuresClinica).allMatch(PlanFeature::isEnabled);

    List<PlanLimit> limitesEsencial = planLimitRepository.findByPlanId(esencial.getId());
    assertThat(limitesEsencial).hasSize(6);
    assertThat(limitValue(limitesEsencial, LimitKey.MAX_PATIENTS)).isEqualTo(150);
    assertThat(limitValue(limitesEsencial, LimitKey.MAX_USERS)).isEqualTo(2);

    // Clínica: pacientes y usuarios ilimitados (NULL en BD)
    Optional<PlanLimit> maxPatientsClinica =
        planLimitRepository.findByPlanIdAndLimitKey(clinica.getId(), LimitKey.MAX_PATIENTS);
    assertThat(maxPatientsClinica).isPresent();
    assertThat(maxPatientsClinica.get().isUnlimited()).isTrue();
    assertThat(maxPatientsClinica.get().getMaxValue()).isNull();
  }

  @Test
  void leerPlanActivoFeaturesYLimitesDeUnTenant() {
    Plan profesional = planRepository.findByCode("profesional").orElseThrow();

    TenantContext.setTenantId(tenantA.getId());
    TenantSubscription suscripcion;
    try {
      suscripcion = tenantSubscriptionRepository.saveAndFlush(
          new TenantSubscription(tenantA.getId(), profesional, Instant.now().plusSeconds(2_592_000)));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<TenantSubscription> activa =
          tenantSubscriptionRepository.findLiveByTenantId(tenantA.getId(), LIVE_STATUSES);

      assertThat(activa).isPresent();
      assertThat(activa.get().getId()).isEqualTo(suscripcion.getId());
      assertThat(activa.get().getPlan().getCode()).isEqualTo("profesional");
      assertThat(activa.get().getStatus()).isEqualTo(SubscriptionStatus.trialing);
      assertThat(activa.get().getBillingCycle()).isEqualTo(BillingCycle.monthly);

      List<PlanFeature> features =
          planFeatureRepository.findByPlanId(activa.get().getPlan().getId());
      assertThat(featureEnabled(features, FeatureKey.CRM_LEADS)).isTrue();
      assertThat(featureEnabled(features, FeatureKey.CARTERA)).isTrue();

      List<PlanLimit> limites =
          planLimitRepository.findByPlanId(activa.get().getPlan().getId());
      assertThat(limitValue(limites, LimitKey.MAX_PATIENTS)).isEqualTo(800);
      assertThat(limitValue(limites, LimitKey.WHATSAPP_CONVERSATIONS_MONTH)).isEqualTo(300);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void impedirDobleSuscripcionVivaPorTenant() {
    Plan profesional = planRepository.findByCode("profesional").orElseThrow();

    TenantContext.setTenantId(tenantA.getId());
    try {
      tenantSubscriptionRepository.saveAndFlush(
          new TenantSubscription(tenantA.getId(), profesional, Instant.now().plusSeconds(2_592_000)));

      TenantSubscription segunda =
          new TenantSubscription(tenantA.getId(), profesional, Instant.now().plusSeconds(2_592_000));
      segunda.setStatus(SubscriptionStatus.active);

      assertThatThrownBy(() -> tenantSubscriptionRepository.saveAndFlush(segunda))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantEstricto() {
    Plan esencial = planRepository.findByCode("esencial").orElseThrow();

    TenantSubscription suscripcionA;
    TenantContext.setTenantId(tenantA.getId());
    try {
      suscripcionA = tenantSubscriptionRepository.saveAndFlush(
          new TenantSubscription(tenantA.getId(), esencial, Instant.now().plusSeconds(2_592_000)));
    } finally {
      TenantContext.clear();
    }

    // Cambiar al contexto de Tenant B
    TenantContext.setTenantId(tenantB.getId());
    try {
      // 1. findById directo sobre la suscripción del tenant A
      Optional<TenantSubscription> encontrada =
          tenantSubscriptionRepository.findById(suscripcionA.getId());
      assertThat(encontrada).isEmpty();

      // 2. Defensa en profundidad: query explícita por tenant tampoco la ve
      Optional<TenantSubscription> porTenant =
          tenantSubscriptionRepository.findByIdAndTenantId(suscripcionA.getId(), tenantB.getId());
      assertThat(porTenant).isEmpty();

      // 3. El tenant B no tiene suscripción viva propia
      Optional<TenantSubscription> vivaB =
          tenantSubscriptionRepository.findLiveByTenantId(tenantB.getId(), LIVE_STATUSES);
      assertThat(vivaB).isEmpty();

      // 4. findAll no incluye la suscripción del tenant A
      List<TenantSubscription> todas = tenantSubscriptionRepository.findAll();
      assertThat(todas).noneMatch(s -> s.getId().equals(suscripcionA.getId()));
    } finally {
      TenantContext.clear();
    }
  }

  private static boolean featureEnabled(List<PlanFeature> features, String key) {
    return features.stream()
        .filter(f -> f.getFeatureKey().equals(key))
        .findFirst()
        .map(PlanFeature::isEnabled)
        .orElseThrow(() -> new AssertionError("Feature no sembrada: " + key));
  }

  private static Integer limitValue(List<PlanLimit> limits, String key) {
    return limits.stream()
        .filter(l -> l.getLimitKey().equals(key))
        .findFirst()
        .map(PlanLimit::getMaxValue)
        .orElseThrow(() -> new AssertionError("Límite no sembrado: " + key));
  }
}

