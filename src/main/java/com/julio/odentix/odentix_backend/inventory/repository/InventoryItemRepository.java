package com.julio.odentix.odentix_backend.inventory.repository;

import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link InventoryItem} (FASE7-03).
 *
 * <p>El filtro automático de tenant (@TenantId de Hibernate) se aplica en todas
 * las queries.
 */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

  /**
   * Busca un ítem por su ID y tenant (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<InventoryItem> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Busca un ítem por nombre dentro del tenant activo.
   *
   * @param name nombre exacto del insumo.
   * @return ítem correspondiente, si existe.
   */
  Optional<InventoryItem> findByName(String name);

  /**
   * Ítems con stock en o por debajo del umbral mínimo (base del endpoint
   * {@code GET /api/v1/inventory/critical} de FASE7-04; usa el índice parcial
   * {@code idx_inventory_items_critical}).
   *
   * <p>Comparar dos columnas ({@code quantity <= minThreshold}) no lo soportan
   * las queries derivadas de Spring Data, por eso va con JPQL explícito — con
   * filtro de {@code tenantId} además del automático @TenantId (defensa en
   * profundidad, regla §5.2 de AGENTS.md).
   */
  @Query("""
      SELECT i FROM InventoryItem i
      WHERE i.tenantId = :tenantId
        AND i.quantity <= i.minThreshold
      """)
  List<InventoryItem> findCritical(@Param("tenantId") UUID tenantId);

  /**
   * Ítems críticos de todos los tenants (insumo de la regla
   * `inventario_critico` de FASE9-02, que corre en contexto de sistema).
   * Usa el índice parcial `idx_inventory_items_critical`.
   */
  @Query("""
      SELECT i FROM InventoryItem i
      WHERE i.quantity <= i.minThreshold
      """)
  List<InventoryItem> findAllCritical();
}
