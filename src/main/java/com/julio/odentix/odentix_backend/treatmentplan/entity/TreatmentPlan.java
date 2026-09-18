package com.julio.odentix.odentix_backend.treatmentplan.entity;

import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Entidad central del negocio odontológico: plan de tratamiento (FASE4-01).
 *
 * <p>Representa la propuesta clínica y económica efectuada a un paciente: diagnóstico,
 * conjunto de procedimientos con precios/descuentos, y estado en el embudo clínico.
 *
 * <p>Hereda de {@link TenantAwareEntity}, garantizando aislamiento multi-tenant
 * automático por {@code tenant_id}.
 *
 * <p>La relación con {@link TreatmentPlanItem} se gestiona en cascada ({@code ALL})
 * con soporte para {@code orphanRemoval}, manteniendo la integridad bidireccional
 * mediante los métodos {@link #addItem(TreatmentPlanItem)} y {@link #removeItem(TreatmentPlanItem)}.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "treatment_plans")
public class TreatmentPlan extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "professional_id")
  private Professional professional;

  @Column(name = "diagnosis", columnDefinition = "TEXT")
  private String diagnosis;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private TreatmentPlanStatus status = TreatmentPlanStatus.borrador;

  @Column(name = "total_price_cop", precision = 12, scale = 2, nullable = false)
  private BigDecimal totalPriceCop = BigDecimal.ZERO;

  @Column(name = "presented_at")
  private Instant presentedAt;

  @Column(name = "last_contact_at")
  private Instant lastContactAt;

  @OneToMany(mappedBy = "treatmentPlan", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TreatmentPlanItem> items = new ArrayList<>();

  public TreatmentPlan() {
    super();
  }

  public TreatmentPlan(UUID tenantId, Patient patient) {
    super(tenantId);
    this.patient = patient;
    this.status = TreatmentPlanStatus.borrador;
    this.totalPriceCop = BigDecimal.ZERO;
  }

  public TreatmentPlan(UUID tenantId, Patient patient, Professional professional, String diagnosis) {
    super(tenantId);
    this.patient = patient;
    this.professional = professional;
    this.diagnosis = diagnosis;
    this.status = TreatmentPlanStatus.borrador;
    this.totalPriceCop = BigDecimal.ZERO;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public Professional getProfessional() {
    return professional;
  }

  public void setProfessional(Professional professional) {
    this.professional = professional;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public TreatmentPlanStatus getStatus() {
    return status;
  }

  public void setStatus(TreatmentPlanStatus status) {
    this.status = status != null ? status : TreatmentPlanStatus.borrador;
  }

  public BigDecimal getTotalPriceCop() {
    return totalPriceCop;
  }

  public void setTotalPriceCop(BigDecimal totalPriceCop) {
    this.totalPriceCop = totalPriceCop != null ? totalPriceCop : BigDecimal.ZERO;
  }

  public Instant getPresentedAt() {
    return presentedAt;
  }

  public void setPresentedAt(Instant presentedAt) {
    this.presentedAt = presentedAt;
  }

  public Instant getLastContactAt() {
    return lastContactAt;
  }

  public void setLastContactAt(Instant lastContactAt) {
    this.lastContactAt = lastContactAt;
  }

  public List<TreatmentPlanItem> getItems() {
    return items;
  }

  public void setItems(List<TreatmentPlanItem> items) {
    this.items = items != null ? items : new ArrayList<>();
    recalculateTotalPrice();
  }

  /**
   * Agrega un ítem a la lista, manteniendo la relación bidireccional,
   * asignando el tenant si no está definido y recalculando el total.
   *
   * @param item ítem a asociar.
   */
  public void addItem(TreatmentPlanItem item) {
    if (item == null) {
      return;
    }
    items.add(item);
    item.setTreatmentPlan(this);
    if (item.getTenantId() == null && this.getTenantId() != null) {
      item.setTenantId(this.getTenantId());
    }
    recalculateTotalPrice();
  }

  /**
   * Remueve un ítem de la lista y recalcula el total acumulado.
   *
   * @param item ítem a remover.
   */
  public void removeItem(TreatmentPlanItem item) {
    if (item == null) {
      return;
    }
    if (items.remove(item)) {
      item.setTreatmentPlan(null);
      recalculateTotalPrice();
    }
  }

  /**
   * Recalcula el monto total {@code total_price_cop} sumando el valor neto
   * ({@code price_cop - discount_cop}) de todos los ítems activos.
   */
  public void recalculateTotalPrice() {
    BigDecimal sum = BigDecimal.ZERO;
    for (TreatmentPlanItem item : items) {
      sum = sum.add(item.getNetPrice());
    }
    this.totalPriceCop = sum.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : sum;
  }
}
