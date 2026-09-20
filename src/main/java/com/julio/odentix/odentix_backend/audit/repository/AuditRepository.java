package com.julio.odentix.odentix_backend.audit.repository;

import com.julio.odentix.odentix_backend.audit.entity.AuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio JPA para AuditLog (FASE1-13).
 *
 * <p>La tabla es append-only: solo lectura filtrada por tenant. Todas las
 * consultas filtran obligatoriamente por tenantId (defensa en profundidad por tenant), además del filtro automático @TenantId.
 */
@Repository
public interface AuditRepository extends JpaRepository<AuditLog, UUID> {

  List<AuditLog> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}

