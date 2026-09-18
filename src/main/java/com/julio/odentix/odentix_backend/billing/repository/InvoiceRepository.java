package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Invoice} (FASE4-03).
 *
 * <p>Mínimo para este ticket (modelo); la generación de facturas y pagos
 * llega en FASE4-04.
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

  /**
   * Busca una factura por ID dentro del tenant activo.
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<Invoice> findByIdAndTenantId(UUID id, UUID tenantId);
}
