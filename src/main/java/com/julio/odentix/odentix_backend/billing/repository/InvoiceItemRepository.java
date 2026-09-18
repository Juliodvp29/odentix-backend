package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.InvoiceItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link InvoiceItem} (FASE4-03).
 */
@Repository
public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {

  /**
   * Ítems de una factura dentro del tenant activo (doble filtro de tenant,
   * regla §5.2 de AGENTS.md).
   */
  List<InvoiceItem> findAllByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
}
