package com.julio.odentix.odentix_backend.billing.entity;

import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Factura simple por paciente (FASE4-03).
 *
 * <p>Sin facturación electrónica (integración futura). Hereda de
 * {@link TenantAwareEntity} para aislamiento automático por tenant.
 * El número es único por tenant (no global). La generación del número y
 * el cálculo de totales viven en el servicio de FASE4-04; esta entidad
 * es solo el modelo de datos.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(
    name = "invoices",
    uniqueConstraints = {
        @UniqueConstraint(name = "uq_invoices_tenant_number", columnNames = {"tenant_id", "invoice_number"})
    }
)
public class Invoice extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "treatment_plan_id")
  private TreatmentPlan treatmentPlan;

  @Column(name = "invoice_number", nullable = false)
  private String invoiceNumber;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private InvoiceStatus status = InvoiceStatus.pendiente;

  @Column(name = "subtotal_cop", nullable = false)
  private BigDecimal subtotalCop = BigDecimal.ZERO;

  @Column(name = "discount_cop", nullable = false)
  private BigDecimal discountCop = BigDecimal.ZERO;

  @Column(name = "total_cop", nullable = false)
  private BigDecimal totalCop = BigDecimal.ZERO;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  public Invoice() {
    super();
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public TreatmentPlan getTreatmentPlan() {
    return treatmentPlan;
  }

  public void setTreatmentPlan(TreatmentPlan treatmentPlan) {
    this.treatmentPlan = treatmentPlan;
  }

  public String getInvoiceNumber() {
    return invoiceNumber;
  }

  public void setInvoiceNumber(String invoiceNumber) {
    this.invoiceNumber = invoiceNumber;
  }

  public InvoiceStatus getStatus() {
    return status;
  }

  public void setStatus(InvoiceStatus status) {
    this.status = status != null ? status : InvoiceStatus.pendiente;
  }

  public BigDecimal getSubtotalCop() {
    return subtotalCop;
  }

  public void setSubtotalCop(BigDecimal subtotalCop) {
    this.subtotalCop = subtotalCop != null ? subtotalCop : BigDecimal.ZERO;
  }

  public BigDecimal getDiscountCop() {
    return discountCop;
  }

  public void setDiscountCop(BigDecimal discountCop) {
    this.discountCop = discountCop != null ? discountCop : BigDecimal.ZERO;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }

  public void setTotalCop(BigDecimal totalCop) {
    this.totalCop = totalCop != null ? totalCop : BigDecimal.ZERO;
  }

  public Instant getIssuedAt() {
    return issuedAt;
  }

  public void setIssuedAt(Instant issuedAt) {
    this.issuedAt = issuedAt;
  }

  @Override
  public String toString() {
    // Sin montos en logs (datos financieros del tenant, regla §5.5 de AGENTS.md).
    return "Invoice{"
        + "id=" + getId()
        + ", invoiceNumber='" + invoiceNumber + '\''
        + ", status=" + status
        + '}';
  }
}
