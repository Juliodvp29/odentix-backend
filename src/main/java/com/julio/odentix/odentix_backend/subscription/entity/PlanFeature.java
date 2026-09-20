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
 * Feature flag de un plan (FASE11-01): ¿el tenant suscrito tiene acceso al
 * módulo o no? (diseño de la sección "Pricing y límites por plan" del roadmap).
 *
 * <p>Catálogo global como {@link Plan}: sin {@code tenant_id} ni RLS.
 * Las claves válidas están en {@link FeatureKey}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "plan_features",
    uniqueConstraints = @UniqueConstraint(columnNames = {"plan_id", "feature_key"}))
public class PlanFeature {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  private Plan plan;

  @Column(name = "feature_key", nullable = false)
  private String featureKey;

  @Column(nullable = false)
  private boolean enabled;

  public PlanFeature() {
  }

  public PlanFeature(Plan plan, String featureKey, boolean enabled) {
    this.plan = plan;
    this.featureKey = featureKey;
    this.enabled = enabled;
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

  public String getFeatureKey() {
    return featureKey;
  }

  public void setFeatureKey(String featureKey) {
    this.featureKey = featureKey;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PlanFeature that = (PlanFeature) o;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "PlanFeature{"
        + "id=" + id
        + ", featureKey='" + featureKey + '\''
        + ", enabled=" + enabled
        + '}';
  }
}

