package com.julio.odentix.odentix_backend.notification.repository;

import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.entity.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Notification} (FASE8-03).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /**
   * Busca una notificación por su ID y tenant (defensa en profundidad por tenant).
   */
  Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Historial de intentos hacia un destinatario en un canal.
   */
  List<Notification> findByChannelAndRecipient(NotificationChannel channel, String recipient);

  /**
   * Consumo del canal en el periodo (insumo de la cuota mensual de WhatsApp de
   * FASE11-03: solo cuentan los envíos efectivos, no los intentos fallidos).
   */
  long countByTenantIdAndChannelAndStatusAndCreatedAtGreaterThanEqual(
      UUID tenantId,
      NotificationChannel channel,
      NotificationStatus status,
      Instant since);
}

