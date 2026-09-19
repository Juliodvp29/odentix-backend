package com.julio.odentix.odentix_backend.notification.repository;

import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data JPA para {@link Notification} (FASE8-03).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

  /**
   * Busca una notificación por su ID y tenant (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<Notification> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Historial de intentos hacia un destinatario en un canal.
   */
  List<Notification> findByChannelAndRecipient(NotificationChannel channel, String recipient);
}
