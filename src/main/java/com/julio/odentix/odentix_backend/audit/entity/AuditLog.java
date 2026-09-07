package com.julio.odentix.odentix_backend.audit.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

/**
 * Entrada de auditoría (FASE1-13).
 *
 * <p>Hereda de {@link TenantAwareEntity}: el {@code tenant_id} se filtra
 * automáticamente (@TenantId, FASE1-09) y nunca se exponen entradas de otro
 * tenant. Tabla append-only: no hay setters de actualización ni borrado.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends TenantAwareEntity {

  @Column(name = "user_id")
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(nullable = false)
  private AuditAction action;

  @Column(name = "entity_name", nullable = false)
  private String entityName;

  @Column(name = "entity_id")
  private UUID entityId;

  // Detalle libre en JSONB (ej. {"email": "..."}). Anulable.
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> detail;

  public AuditLog() {
    super();
  }

  public AuditLog(
      UUID tenantId,
      UUID userId,
      AuditAction action,
      String entityName,
      UUID entityId,
      Map<String, Object> detail) {
    super(tenantId);
    this.userId = userId;
    this.action = action;
    this.entityName = entityName;
    this.entityId = entityId;
    this.detail = detail;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public AuditAction getAction() {
    return action;
  }

  public void setAction(AuditAction action) {
    this.action = action;
  }

  public String getEntityName() {
    return entityName;
  }

  public void setEntityName(String entityName) {
    this.entityName = entityName;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public void setEntityId(UUID entityId) {
    this.entityId = entityId;
  }

  public Map<String, Object> getDetail() {
    return detail;
  }

  public void setDetail(Map<String, Object> detail) {
    this.detail = detail;
  }

  @Override
  public String toString() {
    // Nunca incluir el detail completo: puede contener datos sensibles del tenant.
    return "AuditLog{"
        + "id=" + getId()
        + ", action=" + action
        + ", entityName='" + entityName + '\''
        + '}';
  }
}
