package com.julio.odentix.odentix_backend.billing.repository;

import com.julio.odentix.odentix_backend.billing.entity.Invoice;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

  /**
   * Producción bruta facturada de un profesional en un periodo (FASE7-02).
   *
   * <p>Suma {@code total_cop} de las facturas emitidas ({@code issued_at} en el
   * rango) vinculadas a tratamientos del profesional, excluyendo anuladas. Las
   * facturas sin plan de tratamiento asociado no cuentan (join interno): solo
   * la producción trazable a un tratamiento entra a la liquidación.
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md).
   *
   * @return suma facturada, o cero si no hay facturas en el periodo.
   */
  @Query("""
      SELECT COALESCE(SUM(i.totalCop), 0)
      FROM Invoice i
      WHERE i.tenantId = :tenantId
        AND i.treatmentPlan.professional.id = :professionalId
        AND i.status <> :excludedStatus
        AND i.issuedAt >= :desde
        AND i.issuedAt < :hastaExclusivo
      """)
  BigDecimal sumFacturadoPorProfesionalEnPeriodo(
      @Param("tenantId") UUID tenantId,
      @Param("professionalId") UUID professionalId,
      @Param("excludedStatus") InvoiceStatus excludedStatus,
      @Param("desde") Instant desde,
      @Param("hastaExclusivo") Instant hastaExclusivo);
}
