package com.julio.odentix.odentix_backend.subscription.repository;

import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para suscripciones de tenants (FASE11-01).
 *
 * <p>Aislamiento multi-tenant garantizado automáticamente por {@code @TenantId}
 * en {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity}.
 * Métodos con {@code tenantId} explícito se proporcionan por defensa en profundidad
 * (regla §5 de AGENTS.md): nunca una query de negocio sin filtrar por tenant.
 */
@Repository
public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

  Optional<TenantSubscription> findByIdAndTenantId(UUID id, UUID tenantId);

  List<TenantSubscription> findByTenantId(UUID tenantId);

  /**
   * Suscripción "viva" del tenant (trialing, active o past_due). Por el índice
   * único parcial de V25 como máximo hay una.
   *
   * @param tenantId tenant consultado.
   * @param statuses estados considerados vivos.
   * @return la suscripción activa, si existe.
   */
  @Query("""
      SELECT ts FROM TenantSubscription ts
      JOIN FETCH ts.plan
      WHERE ts.tenantId = :tenantId AND ts.status IN :statuses
      """)
  Optional<TenantSubscription> findLiveByTenantId(
      @Param("tenantId") UUID tenantId,
      @Param("statuses") Collection<SubscriptionStatus> statuses);
}
