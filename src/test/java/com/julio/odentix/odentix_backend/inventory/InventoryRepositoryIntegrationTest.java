package com.julio.odentix.odentix_backend.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.entity.StockMovement;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.inventory.repository.StockMovementRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para {@link InventoryItem} y {@link StockMovement}
 * (FASE7-03) contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Insertar un {@code StockMovement} actualiza automáticamente la cantidad
 *       del {@code InventoryItem} — verificado por el trigger, sin lógica Java
 *       redundante.</li>
 *   <li>Un movimiento que dejaría el stock en negativo falla por el CHECK de la
 *       base (la transacción revierte y el stock queda intacto).</li>
 *   <li>Aislamiento cross-tenant: un usuario del tenant B no ve los ítems del
 *       tenant A, y un movimiento de B sobre un ítem de A lo rechaza el propio
 *       trigger (regla §5 de AGENTS.md).</li>
 * </ul>
 *
 * <p>Detalle importante: el trigger actúa fuera de JPA, así que después de cada
 * movimiento se limpia el contexto de persistencia y se re-lee el ítem para ver
 * el stock aplicado (la instancia en memoria conserva el valor anterior).
 */
class InventoryRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private InventoryItemRepository inventoryItemRepository;

  @Autowired
  private StockMovementRepository stockMovementRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @PersistenceContext
  private EntityManager entityManager;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Inventario Alfa", "950111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Inventario Beta", "951333444-2"));
  }

  // ---------------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------------

  private InventoryItem crearItem(UUID tenantId, String nombre, int umbral) {
    TenantContext.setTenantId(tenantId);
    try {
      InventoryItem item = new InventoryItem(tenantId, nombre);
      item.setUnit("unidad");
      item.setMinThreshold(umbral);
      return inventoryItemRepository.saveAndFlush(item);
    } finally {
      TenantContext.clear();
    }
  }

  private void moverStock(UUID tenantId, InventoryItem item, int delta, String motivo) {
    TenantContext.setTenantId(tenantId);
    try {
      StockMovement movement = new StockMovement(tenantId, item, delta);
      movement.setReason(motivo);
      stockMovementRepository.saveAndFlush(movement);
    } finally {
      TenantContext.clear();
    }
  }

  private int stockActual(UUID tenantId, UUID itemId) {
    // Re-lee desde BD: el trigger modificó la fila fuera del contexto JPA.
    entityManager.clear();
    TenantContext.setTenantId(tenantId);
    try {
      return inventoryItemRepository.findById(itemId).orElseThrow().getQuantity();
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de comportamiento normal (DoD FASE7-03)
  // ---------------------------------------------------------------------------

  @Test
  void entradaActualizaStockAutomaticamente() {
    InventoryItem item = crearItem(tenantA.getId(), "Resina compuesta", 5);
    assertThat(stockActual(tenantA.getId(), item.getId())).isZero();

    moverStock(tenantA.getId(), item, 10, "Compra inicial");

    assertThat(stockActual(tenantA.getId(), item.getId())).isEqualTo(10);

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<StockMovement> historial = stockMovementRepository
          .findByInventoryItemIdOrderByCreatedAtDesc(item.getId());
      assertThat(historial).hasSize(1);
      assertThat(historial.get(0).getQuantityDelta()).isEqualTo(10);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void salidaReduceStock() {
    InventoryItem item = crearItem(tenantA.getId(), "Guantes", 20);
    moverStock(tenantA.getId(), item, 10, "Compra");
    moverStock(tenantA.getId(), item, -4, "Consumo en tratamiento");

    assertThat(stockActual(tenantA.getId(), item.getId())).isEqualTo(6);
  }

  @Test
  void itemsCriticosSeDetectanPorUmbral() {
    InventoryItem critico = crearItem(tenantA.getId(), "Anestesia", 5);
    InventoryItem sano = crearItem(tenantA.getId(), "Algodón", 5);
    moverStock(tenantA.getId(), critico, 3, "Compra parcial");
    moverStock(tenantA.getId(), sano, 50, "Compra");

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<InventoryItem> criticos =
          inventoryItemRepository.findCritical(tenantA.getId());
      assertThat(criticos).extracting(InventoryItem::getId)
          .containsExactly(critico.getId());
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Tests de constraints de BD
  // ---------------------------------------------------------------------------

  @Test
  void consumoQueDejaNegativoFallaYStockQuedaIntacto() {
    InventoryItem item = crearItem(tenantA.getId(), "Implante", 1);
    moverStock(tenantA.getId(), item, 2, "Compra");

    TenantContext.setTenantId(tenantA.getId());
    try {
      StockMovement exceso = new StockMovement(tenantA.getId(), item, -5);
      exceso.setReason("Consumo excesivo");
      // El CHECK de quantity >= 0 revierte toda la transacción.
      assertThatThrownBy(() -> stockMovementRepository.saveAndFlush(exceso))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }

    assertThat(stockActual(tenantA.getId(), item.getId())).isEqualTo(2);
  }

  @Test
  void deltaCeroEsRechazado() {
    InventoryItem item = crearItem(tenantA.getId(), "Sutura", 2);

    TenantContext.setTenantId(tenantA.getId());
    try {
      StockMovement sinEfecto = new StockMovement(tenantA.getId(), item, 0);
      assertThatThrownBy(() -> stockMovementRepository.saveAndFlush(sinEfecto))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void nombreDuplicadoEnMismoTenantFallaPeroEnOtroTenantOk() {
    crearItem(tenantA.getId(), "Brackets", 10);

    TenantContext.setTenantId(tenantA.getId());
    try {
      InventoryItem duplicado = new InventoryItem(tenantA.getId(), "Brackets");
      assertThatThrownBy(() -> inventoryItemRepository.saveAndFlush(duplicado))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }

    // El mismo nombre en otra clínica es válido (UNIQUE por tenant).
    InventoryItem homonimo = crearItem(tenantB.getId(), "Brackets", 3);
    assertThat(homonimo.getId()).isNotNull();
  }

  // ---------------------------------------------------------------------------
  // Test de aislamiento cross-tenant (regla §5 de AGENTS.md — obligatorio)
  // ---------------------------------------------------------------------------

  @Test
  void tenantBNoPuedeVerNiMoverStockDelTenantA() {
    InventoryItem itemA = crearItem(tenantA.getId(), "Corona", 2);
    moverStock(tenantA.getId(), itemA, 7, "Compra");
    UUID idItemA = itemA.getId();

    TenantContext.setTenantId(tenantB.getId());
    try {
      // Búsqueda directa por ID → vacía, no 403 (no confirma que existe).
      Optional<InventoryItem> resultado = inventoryItemRepository.findById(idItemA);
      assertThat(resultado).isEmpty();

      // Query ingenua → solo lo propio (nada, tenantB no tiene ítems).
      assertThat(inventoryItemRepository.findAll()).isEmpty();

      // El propio trigger rechaza un movimiento de B sobre el ítem de A
      // (mismatch de tenant_id → NOT FOUND → RAISE EXCEPTION).
      StockMovement invasivo = new StockMovement(tenantB.getId(), itemA, -1);
      invasivo.setReason("Intento cross-tenant");
      assertThatThrownBy(() -> stockMovementRepository.saveAndFlush(invasivo))
          .isInstanceOf(DataAccessException.class)
          .hasStackTraceContaining("no encontrado");
    } finally {
      TenantContext.clear();
    }

    // El stock de A quedó intacto.
    assertThat(stockActual(tenantA.getId(), idItemA)).isEqualTo(7);
  }
}
