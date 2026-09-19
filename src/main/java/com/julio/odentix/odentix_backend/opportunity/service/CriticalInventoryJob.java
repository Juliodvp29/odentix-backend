package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: inventario crítico → oportunidad de negocio (FASE9-02).
 *
 * <p>Condición: ítem con {@code quantity <= min_threshold} (usa el índice
 * parcial `idx_inventory_items_critical`). Sin el insumo no se puede atender:
 * es riesgo operativo directo.
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para el mismo ítem, no se crea otra.
 *
 * <p>Prioridad determinística (1 a 5):
 * <ul>
 *   <li>Quiebre total ({@code quantity == 0}) → prioridad 4 (Alta).</li>
 *   <li>Por debajo del umbral → prioridad 3 (Media-alta).</li>
 * </ul>
 * Sin valor monetario directo (cero).
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin filtro de
 * tenant): una sola corrida cubre todas las clínicas. Cada oportunidad hereda el
 * {@code tenantId} de su ítem.
 */
@Service
public class CriticalInventoryJob {

  private static final Logger log = LoggerFactory.getLogger(CriticalInventoryJob.class);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  private final InventoryItemRepository inventoryItemRepository;
  private final OpportunityRepository opportunityRepository;

  public CriticalInventoryJob(
      InventoryItemRepository inventoryItemRepository,
      OpportunityRepository opportunityRepository) {
    this.inventoryItemRepository = inventoryItemRepository;
    this.opportunityRepository = opportunityRepository;
  }

  /**
   * Ejecuta la regla y crea las oportunidades correspondientes.
   *
   * @return total de oportunidades creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de inventario crítico...");

    List<InventoryItem> candidatos = inventoryItemRepository.findAllCritical();

    int creadas = 0;
    for (InventoryItem item : candidatos) {
      boolean yaExiste = opportunityRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "inventory_item", item.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }

      short prioridad = item.getQuantity() == 0 ? (short) 4 : (short) 3;

      Opportunity oportunidad = new Opportunity(
          item.getTenantId(),
          OpportunityType.inventario_critico,
          prioridad);
      oportunidad.setRelatedEntityType("inventory_item");
      oportunidad.setRelatedEntityId(item.getId());
      oportunidad.setEstimatedValueCop(BigDecimal.ZERO);

      opportunityRepository.save(oportunidad);
      creadas++;
    }

    log.info("Job de inventario crítico completado: {} oportunidades creadas.", creadas);
    return creadas;
  }

  /**
   * Disparador programado cada 6 horas con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.critical-inventory-opportunities.cron:0 0 */6 * * *}")
  public void runScheduledJob() {
    execute();
  }
}
