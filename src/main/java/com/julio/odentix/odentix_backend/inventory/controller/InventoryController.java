package com.julio.odentix.odentix_backend.inventory.controller;

import com.julio.odentix.odentix_backend.auth.dto.AuthenticatedUser;
import com.julio.odentix.odentix_backend.inventory.dto.CreateInventoryItemRequest;
import com.julio.odentix.odentix_backend.inventory.dto.CreateStockMovementRequest;
import com.julio.odentix.odentix_backend.inventory.dto.InventoryItemResponse;
import com.julio.odentix.odentix_backend.inventory.dto.StockMovementResponse;
import com.julio.odentix.odentix_backend.inventory.dto.UpdateInventoryItemRequest;
import com.julio.odentix.odentix_backend.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para inventario (FASE7-04).
 *
 * <p>Roles operativos amplios: el stock lo mueven recepción y auxiliar en la
 * práctica diaria (a diferencia de las liquidaciones, no es un pago).
 */
@RestController
@RequestMapping("/api/v1/inventory")
@Tag(name = "Inventario", description = "Insumos, movimientos de stock y alertas de nivel crítico.")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

  private static final String ROLES_OPERATIVOS =
      "hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')";

  private final InventoryService inventoryService;

  public InventoryController(InventoryService inventoryService) {
    this.inventoryService = inventoryService;
  }

  /**
   * Crea un ítem de inventario (el stock nace en cero).
   */
  @PostMapping("/items")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Crear insumo",
      description = "Crea un ítem de inventario con stock inicial en cero. "
          + "Devuelve 409 si ya existe un insumo con ese nombre."
  )
  public ResponseEntity<InventoryItemResponse> crearItem(
      @Valid @RequestBody CreateInventoryItemRequest request) {
    InventoryItemResponse response = inventoryService.crearItem(request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/inventory/items/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Lista todos los ítems del tenant activo.
   */
  @GetMapping("/items")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Listar insumos", description = "Lista todos los ítems de inventario.")
  public ResponseEntity<List<InventoryItemResponse>> listarItems() {
    return ResponseEntity.ok(inventoryService.listarItems());
  }

  /**
   * Ítems con stock en o por debajo del umbral mínimo.
   */
  @GetMapping("/critical")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Insumos críticos",
      description = "Ítems con quantity menor o igual a min_threshold (alerta de reposición)."
  )
  public ResponseEntity<List<InventoryItemResponse>> listarCriticos() {
    return ResponseEntity.ok(inventoryService.listarCriticos());
  }

  /**
   * Obtiene un ítem por ID.
   */
  @GetMapping("/items/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(summary = "Ver insumo", description = "Obtiene un ítem con su stock actual.")
  public ResponseEntity<InventoryItemResponse> obtenerItem(@PathVariable UUID id) {
    return ResponseEntity.ok(inventoryService.obtenerItem(id));
  }

  /**
   * Actualiza nombre, unidad y/o umbral de un ítem.
   */
  @PatchMapping("/items/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Actualizar insumo",
      description = "Actualiza nombre, unidad y/o umbral mínimo. El stock no se edita por aquí."
  )
  public ResponseEntity<InventoryItemResponse> actualizarItem(
      @PathVariable UUID id,
      @Valid @RequestBody UpdateInventoryItemRequest request) {
    return ResponseEntity.ok(inventoryService.actualizarItem(id, request));
  }

  /**
   * Elimina un ítem solo si no tiene movimientos registrados.
   */
  @DeleteMapping("/items/{id}")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Eliminar insumo",
      description = "Elimina un ítem sin movimientos. Devuelve 409 si tiene historial de stock."
  )
  public ResponseEntity<Void> eliminarItem(@PathVariable UUID id) {
    inventoryService.eliminarItem(id);
    return ResponseEntity.noContent().build();
  }

  /**
   * Registra un movimiento de stock (entrada positiva, salida negativa).
   */
  @PostMapping("/items/{id}/movements")
  @PreAuthorize(ROLES_OPERATIVOS)
  @Operation(
      summary = "Registrar movimiento",
      description = "Registra una entrada o salida de stock. "
          + "Devuelve 409 si el consumo dejaría el inventario en negativo."
  )
  public ResponseEntity<StockMovementResponse> registrarMovimiento(
      @PathVariable UUID id,
      @Valid @RequestBody CreateStockMovementRequest request,
      @AuthenticationPrincipal AuthenticatedUser authUser) {
    UUID currentUserId = (authUser != null) ? authUser.getUserId() : null;
    StockMovementResponse response =
        inventoryService.registrarMovimiento(id, request, currentUserId);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/inventory/items/{id}/movements/{movementId}")
        .buildAndExpand(id, response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
