package com.julio.odentix.odentix_backend.inventory.service;

import com.julio.odentix.odentix_backend.inventory.dto.CreateInventoryItemRequest;
import com.julio.odentix.odentix_backend.inventory.dto.CreateStockMovementRequest;
import com.julio.odentix.odentix_backend.inventory.dto.InventoryItemResponse;
import com.julio.odentix.odentix_backend.inventory.dto.StockMovementResponse;
import com.julio.odentix.odentix_backend.inventory.dto.UpdateInventoryItemRequest;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.entity.StockMovement;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.inventory.repository.StockMovementRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para inventario (FASE7-04).
 *
 * <p>Principio central: el stock <b>solo</b> lo mueve el trigger
 * {@code trg_apply_stock_movement} al insertar el movimiento, en la misma
 * transacción. Este servicio nunca recalcula cantidades: crea el movimiento y
 * re-lee el ítem para devolver el stock aplicado.
 *
 * <p>Traducción de errores de BD (precedente de citas solapadas → 409):
 * un consumo que dejaría el stock en negativo lo revierte el CHECK de
 * {@code inventory_items.quantity}; aquí se captura y se devuelve un 409 con
 * mensaje operativo claro. El CHECK sigue siendo el mecanismo autoritativo —
 * el catch solo mejora el mensaje, no sustituye la validación.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: un ítem de otro tenant
 * resulta invisible (404), conforme a la regla §5 de AGENTS.md.
 */
@Service
public class InventoryService {

  private final InventoryItemRepository inventoryItemRepository;
  private final StockMovementRepository stockMovementRepository;

  @PersistenceContext
  private EntityManager entityManager;

  public InventoryService(
      InventoryItemRepository inventoryItemRepository,
      StockMovementRepository stockMovementRepository) {
    this.inventoryItemRepository = inventoryItemRepository;
    this.stockMovementRepository = stockMovementRepository;
  }

  /**
   * Crea un ítem de inventario. El stock nace en cero (las existencias
   * iniciales se registran con un movimiento de entrada).
   */
  @Transactional
  public InventoryItemResponse crearItem(CreateInventoryItemRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    try {
      InventoryItem item = new InventoryItem(tenantId, request.getName().strip());
      item.setUnit(request.getUnit());
      item.setMinThreshold(request.getMinThreshold());
      return aResponse(inventoryItemRepository.saveAndFlush(item));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "Ya existe un insumo con ese nombre en la clínica.");
    }
  }

  /**
   * Lista todos los ítems del tenant activo.
   */
  @Transactional(readOnly = true)
  public List<InventoryItemResponse> listarItems() {
    TenantContext.getRequiredTenantId();
    return inventoryItemRepository.findAll().stream()
        .map(this::aResponse)
        .toList();
  }

  /**
   * Obtiene un ítem por ID dentro del tenant activo.
   */
  @Transactional(readOnly = true)
  public InventoryItemResponse obtenerItem(UUID id) {
    return aResponse(buscarItem(id));
  }

  /**
   * Actualiza nombre, unidad y/o umbral de un ítem. El stock no se edita
   * por aquí — solo vía movimientos.
   */
  @Transactional
  public InventoryItemResponse actualizarItem(UUID id, UpdateInventoryItemRequest request) {
    InventoryItem item = buscarItem(id);

    if (request.getName() != null && !request.getName().isBlank()) {
      item.setName(request.getName().strip());
    }
    if (request.getUnit() != null) {
      item.setUnit(request.getUnit());
    }
    if (request.getMinThreshold() != null) {
      item.setMinThreshold(request.getMinThreshold());
    }
    try {
      return aResponse(inventoryItemRepository.saveAndFlush(item));
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "Ya existe un insumo con ese nombre en la clínica.");
    }
  }

  /**
   * Elimina un ítem solo si no tiene movimientos registrados (el historial
   * de stock no se borra: el FK es en cascada y arrastraría la trazabilidad).
   */
  @Transactional
  public void eliminarItem(UUID id) {
    InventoryItem item = buscarItem(id);

    List<StockMovement> movimientos = stockMovementRepository
        .findByInventoryItemIdOrderByCreatedAtDesc(item.getId());
    if (!movimientos.isEmpty()) {
      throw new ConflictException(
          "El insumo tiene movimientos registrados y no se puede eliminar.");
    }
    inventoryItemRepository.delete(item);
  }

  /**
   * Registra un movimiento de stock (entrada positiva, salida negativa) y
   * devuelve el movimiento con el stock resultante ya aplicado por el trigger.
   *
   * @param id ítem dentro del tenant activo.
   * @param request delta y motivo.
   * @param currentUserId usuario que registra (queda en {@code created_by}).
   */
  @Transactional
  public StockMovementResponse registrarMovimiento(
      UUID id, CreateStockMovementRequest request, UUID currentUserId) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    InventoryItem item = buscarItem(id);

    int delta = request.getQuantityDelta();
    if (delta == 0) {
      throw new IllegalArgumentException("quantityDelta no puede ser cero.");
    }

    StockMovement movement = new StockMovement(tenantId, item, delta);
    movement.setReason(request.getReason());
    movement.setCreatedBy(currentUserId);
    try {
      movement = stockMovementRepository.saveAndFlush(movement);
    } catch (DataIntegrityViolationException e) {
      throw new ConflictException(
          "Stock insuficiente: el consumo dejaría el inventario en negativo.");
    }

    // El trigger ya aplicó el delta; se re-lee para devolver el resultante
    // (la instancia en memoria conserva el valor anterior).
    entityManager.refresh(item);

    StockMovementResponse response = new StockMovementResponse();
    response.setId(movement.getId());
    response.setInventoryItemId(item.getId());
    response.setQuantityDelta(delta);
    response.setReason(movement.getReason());
    response.setResultingQuantity(item.getQuantity());
    response.setCreatedAt(movement.getCreatedAt());
    return response;
  }

  /**
   * Ítems con stock en o por debajo del umbral mínimo (usa el índice parcial
   * {@code idx_inventory_items_critical}).
   */
  @Transactional(readOnly = true)
  public List<InventoryItemResponse> listarCriticos() {
    UUID tenantId = TenantContext.getRequiredTenantId();
    return inventoryItemRepository.findCritical(tenantId).stream()
        .map(this::aResponse)
        .toList();
  }

  private InventoryItem buscarItem(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    return inventoryItemRepository.findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Insumo no encontrado: " + id));
  }

  private InventoryItemResponse aResponse(InventoryItem item) {
    InventoryItemResponse response = new InventoryItemResponse();
    response.setId(item.getId());
    response.setName(item.getName());
    response.setUnit(item.getUnit());
    response.setQuantity(item.getQuantity());
    response.setMinThreshold(item.getMinThreshold());
    return response;
  }
}
