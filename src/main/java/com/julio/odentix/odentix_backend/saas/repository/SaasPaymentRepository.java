package com.julio.odentix.odentix_backend.saas.repository;

import com.julio.odentix.odentix_backend.saas.entity.SaasPayment;
import com.julio.odentix.odentix_backend.saas.entity.SaasPaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para cobros del SaaS (FASE11-04).
 *
 * <p>El filtro automático de tenant (@TenantId) aplica en todas las queries.
 */
@Repository
public interface SaasPaymentRepository extends JpaRepository<SaasPayment, UUID> {

  /**
   * Busca un cobro por la referencia enviada a Bold (llega en
   * `data.metadata.reference` del webhook).
   */
  Optional<SaasPayment> findByBoldReference(String boldReference);

  /**
   * Indica si una notificación de Bold ya fue procesada (idempotencia ante
   * reintentos del webhook).
   */
  boolean existsByBoldNotificationId(String boldNotificationId);

  /**
   * Cobros pendientes de una suscripción (para no generar links duplicados al renovar).
   */
  List<SaasPayment> findBySubscriptionIdAndStatus(UUID subscriptionId, SaasPaymentStatus status);
}
