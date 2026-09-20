package com.julio.odentix.odentix_backend.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import java.util.UUID;

/**
 * Límite numérico de un plan (FASE11-01): ¿cuántos recursos puede tener/usar
 * el tenant suscrito? {@code maxValue} en {@code NULL} significa "ilimitado"
 * (convención del roadmap, sección "Pricing y límites por plan").
 *
 * <p>Catálogo global como {@link Plan}: sin {@code tenant_id} ni RLS.
 * Las claves válidas están en {@link LimitKey}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "plan_limits",
    uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "limit_key"}))
public class PlanLimit {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Column(name = "limit_key", nullable = false)
  private String limitKey;

  @Column(name = "max_value")
  private Integer maxValue;

  public PlanLimit() {
  }

  public PlanLimit(Plan plan, String limitKey, Integer maxValue) {
    this.plan = plan;
    this.limitKey = limitKey;
    this.maxValue = maxValue;
  }

  /**
   * Indica si el límite es "ilimitado" ({@code max_value} NULL en BD).
   *
   * @return true si no hay tope numérico.
   */
  public boolean isUnlimited() {
    return maxValue == null;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Plan getPlan() {
    return plan;
  }

  public void setPlan(Plan plan) {
    this.plan = plan;
  }

  public String getLimitKey() {
    return limitKey;
  }

  public void setLimitKey(String limitKey) {
    this.limitKey = limitKey;
  }

  public Integer getMaxValue() {
    return maxValue;
  }

  public void setMaxValue(Integer maxValue) {
    this.maxValue = maxValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlanLimit planLimit = (PlanLimit) o;
    return id != null && Objects.equals(id, planLimit.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "PlanLimit{"
        + "id=" + id
        + ", limitKey='" + limitKey + '\''
        + ", maxValue=" + maxValue
        + '}';
  }
}

