package com.julio.odentix.odentix_backend.crm.repository;

import com.julio.odentix.odentix_backend.crm.entity.LeadActivity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para el historial de interacciones con prospectos (FASE5-01).
 *
 * <p>Aislamiento multi-tenant garantizado automáticamente por {@code @TenantId}
 * en {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity}.
 */
@Repository
public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

  Optional<LeadActivity> findByIdAndTenantId(UUID id, UUID tenantId);

  List<LeadActivity> findByLeadIdOrderByCreatedAtDesc(UUID leadId);

  List<LeadActivity> findByLeadIdAndTenantIdOrderByCreatedAtDesc(UUID leadId, UUID tenantId);

  @org.springframework.data.jpa.repository.Query("""
      SELECT la FROM LeadActivity la
      LEFT JOIN FETCH la.user
      WHERE la.lead.id = :leadId AND la.tenantId = :tenantId
      ORDER BY la.createdAt DESC
  """)
  List<LeadActivity> findByLeadIdAndTenantIdOrderByCreatedAtDescWithUser(
      @org.springframework.data.repository.query.Param("leadId") UUID leadId,
      @org.springframework.data.repository.query.Param("tenantId") UUID tenantId
  );

  long countByTenantIdAndLeadId(UUID tenantId, UUID leadId);
}
