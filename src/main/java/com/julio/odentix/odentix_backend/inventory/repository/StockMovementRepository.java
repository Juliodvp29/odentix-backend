package com.julio.odentix.odentix_backend.inventory.repository;

import com.julio.odentix.odentix_backend.inventory.entity.StockMovement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link StockMovement} (FASE7-03).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  /**
   * Historial de movimientos de un ítem, del más reciente al más antiguo.
   *
   * @param inventoryItemId identificador del ítem.
   * @return movimientos del ítem en el tenant activo.
   */
  List<StockMovement> findByInventoryItemIdOrderByCreatedAtDesc(UUID inventoryItemId);
}
