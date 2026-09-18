package com.julio.odentix.odentix_backend.specialist.entity;

import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Extensión financiera de {@link Professional} para especialistas externos (FASE7-01,
 * sección 8.13 del documento de arquitectura: honorarios y liquidaciones).
 *
 * <p>Relación 1—1 con {@code Professional} ({@code professional_id UNIQUE} en BD).
 * Solo admite profesionales marcados con {@code is_external = true}: el trigger
 * {@code trg_check_specialist_is_external} (V19, reutilizado de {@code schema.sql})
 * lo garantiza en BD, y el callback {@link #validarProfesionalExterno()} lo
 * repite en aplicación — defensa en profundidad, igual que con multi-tenancy
 * (regla §5 de AGENTS.md): ninguna capa sustituye a la otra.
 *
 * <p>Hereda de {@link TenantAwareEntity} para aislamiento automático por tenant.
 * El cálculo de producción bruta y el endpoint de liquidación llegan en FASE7-02;
 * aquí solo el modelo.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "specialists")
public class Specialist extends TenantAwareEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "professional_id", nullable = false, unique = true)
  private Professional professional;

  @Column(name = "fee_percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal feePercentage;

  @Column(name = "payment_terms")
  private String paymentTerms;

  public Specialist() {
    super();
  }

  public Specialist(UUID tenantId, Professional professional, BigDecimal feePercentage) {
    super(tenantId);
    this.professional = professional;
    setFeePercentage(feePercentage);
  }

  /**
   * Validación de aplicación: el profesional debe ser externo.
   *
   * <p>Segunda capa junto al trigger de BD. Se ejecuta en JPA para que un uso
   * normal del repositorio falle rápido con un mensaje claro, sin depender de
   * traducir el error SQL del trigger.
   */
  @PrePersist
  @PreUpdate
  protected void validarProfesionalExterno() {
    if (professional != null && !professional.isExternal()) {
      throw new IllegalStateException(
          "professional " + professional.getId()
              + " debe tener is_external = true para tener un registro en specialists");
    }
  }

  public Professional getProfessional() {
    return professional;
  }

  public void setProfessional(Professional professional) {
    this.professional = professional;
  }

  public BigDecimal getFeePercentage() {
    return feePercentage;
  }

  public void setFeePercentage(BigDecimal feePercentage) {
    if (feePercentage != null
        && (feePercentage.compareTo(BigDecimal.ZERO) < 0
            || feePercentage.compareTo(new BigDecimal("100")) > 0)) {
      throw new IllegalArgumentException("fee_percentage debe estar entre 0 y 100");
    }
    this.feePercentage = feePercentage;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
  }

  @Override
  public String toString() {
    // Sin montos ni términos en logs (datos financieros del tenant, regla §5.5 de AGENTS.md).
    return "Specialist{"
        + "id=" + getId()
        + ", professionalId=" + (professional != null ? professional.getId() : null)
        + '}';
  }
}
