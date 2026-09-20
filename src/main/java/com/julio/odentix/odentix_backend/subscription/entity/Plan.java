package com.julio.odentix.odentix_backend.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plan comercial del SaaS (FASE11-01): Esencial, Profesional, Clínica.
 *
 * <p>Catálogo GLOBAL, no tenant-specific: sin {@code tenant_id} ni RLS, todos
 * los tenants leen las mismas filas (docs/schema.sql §4). Por eso no hereda
 * de {@code TenantAwareEntity} — igual que {@code Tenant} es una excepción
 * documentada de esa convención.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "plans")
public class Plan {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(nullable = false)
  private String name;

  @Column(name = "monthly_price_cop", precision = 12, scale = 2, nullable = false)
  private BigDecimal monthlyPriceCop;

  @Column(name = "annual_price_cop", precision = 12, scale = 2, nullable = false)
  private BigDecimal annualPriceCop;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Plan() {
  }

  public Plan(String code, String name, BigDecimal monthlyPriceCop, BigDecimal annualPriceCop) {
    this.code = code;
    this.name = name;
    this.monthlyPriceCop = monthlyPriceCop;
    this.annualPriceCop = annualPriceCop;
    this.active = true;
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getMonthlyPriceCop() {
    return monthlyPriceCop;
  }

  public void setMonthlyPriceCop(BigDecimal monthlyPriceCop) {
    this.monthlyPriceCop = monthlyPriceCop;
  }

  public BigDecimal getAnnualPriceCop() {
    return annualPriceCop;
  }

  public void setAnnualPriceCop(BigDecimal annualPriceCop) {
    this.annualPriceCop = annualPriceCop;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Plan plan = (Plan) o;
    return id != null && Objects.equals(id, plan.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }

  @Override
  public String toString() {
    return "Plan{"
        + "id=" + id
        + ", code='" + code + '\''
        + ", name='" + name + '\''
        + ", monthlyPriceCop=" + monthlyPriceCop
        + ", annualPriceCop=" + annualPriceCop
        + ", active=" + active
        + '}';
  }
}

