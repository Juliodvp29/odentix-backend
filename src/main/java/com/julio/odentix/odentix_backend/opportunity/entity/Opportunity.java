package com.julio.odentix.odentix_backend.opportunity.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Oportunidad de negocio detectada automáticamente por el motor de reglas (FASE9-01).
 *
 * <p>Cada oportunidad representa una situación donde la clínica puede actuar para
 * recuperar ingresos, mejorar la atención o evitar pérdidas. La referencia
 * polimórfica ({@code relatedEntityType} / {@code relatedEntityId}) apunta a la
 * entidad originadora (treatment_plan, lead, appointment, etc.) sin FK real —
 * mismo precedente que {@code Task} en FASE8-01.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "opportunities")
public class Opportunity extends TenantAwareEntity {

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "type", nullable = false)
  private OpportunityType type;

  @Column(name = "related_entity_type")
  private String relatedEntityType;

  @Column(name = "related_entity_id")
  private UUID relatedEntityId;

  @Column(name = "estimated_value_cop", precision = 12, scale = 2)
  private BigDecimal estimatedValueCop = BigDecimal.ZERO;

  @Column(name = "priority", nullable = false)
  private short priority = 1;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private OpportunityStatus status = OpportunityStatus.abierta;

  @Column(name = "detected_at", nullable = false)
  private Instant detectedAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  public Opportunity() {
    super();
  }

  /**
   * Constructor para creación desde un job de detección.
   *
   * @param tenantId    tenant dueño de esta oportunidad.
   * @param type        tipo de oportunidad detectada.
   * @param priority    prioridad calculada (1 a 5).
   */
  public Opportunity(UUID tenantId, OpportunityType type, short priority) {
    super(tenantId);
    this.type = type;
    this.priority = priority;
    this.detectedAt = Instant.now();
  }

  // --- Getters y setters ---

  public OpportunityType getType() {
    return type;
  }

  public void setType(OpportunityType type) {
    this.type = type;
  }

  public String getRelatedEntityType() {
    return relatedEntityType;
  }

  public void setRelatedEntityType(String relatedEntityType) {
    this.relatedEntityType = relatedEntityType;
  }

  public UUID getRelatedEntityId() {
    return relatedEntityId;
  }

  public void setRelatedEntityId(UUID relatedEntityId) {
    this.relatedEntityId = relatedEntityId;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public void setEstimatedValueCop(BigDecimal estimatedValueCop) {
    this.estimatedValueCop = estimatedValueCop != null ? estimatedValueCop : BigDecimal.ZERO;
  }

  public short getPriority() {
    return priority;
  }

  public void setPriority(short priority) {
    this.priority = priority;
  }

  public OpportunityStatus getStatus() {
    return status;
  }

  public void setStatus(OpportunityStatus status) {
    this.status = status != null ? status : OpportunityStatus.abierta;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }

  public void setDetectedAt(Instant detectedAt) {
    this.detectedAt = detectedAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public void setResolvedAt(Instant resolvedAt) {
    this.resolvedAt = resolvedAt;
  }

  @Override
  public String toString() {
    return "Opportunity{"
        + "id=" + getId()
        + ", type=" + type
        + ", status=" + status
        + ", priority=" + priority
        + '}';
  }
}
