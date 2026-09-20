package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.Payment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Payment} (FASE4-03).
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  /**
   * Pagos de una factura dentro del tenant activo (doble filtro por tenant).
   */
  List<Payment> findAllByTenantIdAndInvoiceId(UUID tenantId, UUID invoiceId);
}


