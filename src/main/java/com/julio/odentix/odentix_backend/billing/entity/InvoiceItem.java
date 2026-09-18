package com.julio.odentix.odentix_backend.billing.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ítem de factura (FASE4-03).
 *
 * <p>{@code totalCop} lo calcula la BD (columna GENERATED): se mapea solo
 * lectura para poder exponerlo sin que Java intente escribirlo nunca.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "invoice_items")
public class InvoiceItem extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  private Invoice invoice;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "quantity", nullable = false)
  private int quantity = 1;

  @Column(name = "unit_price_cop", nullable = false)
  private BigDecimal unitPriceCop = BigDecimal.ZERO;

  // Columna GENERATED ALWAYS AS (quantity * unit_price_cop) STORED:
  // la BD la calcula al insertar; Java solo la lee.
  @Column(name = "total_cop", insertable = false, updatable = false)
  private BigDecimal totalCop;

  public InvoiceItem() {
    super();
  }

  public InvoiceItem(UUID tenantId, Invoice invoice, String description, int quantity, BigDecimal unitPriceCop) {
    super(tenantId);
    this.invoice = invoice;
    this.description = description;
    this.quantity = quantity;
    this.unitPriceCop = unitPriceCop != null ? unitPriceCop : BigDecimal.ZERO;
  }

  public Invoice getInvoice() {
    return invoice;
  }

  public void setInvoice(Invoice invoice) {
    this.invoice = invoice;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public int getQuantity() {
    return quantity;
  }

  public void setQuantity(int quantity) {
    this.quantity = quantity;
  }

  public BigDecimal getUnitPriceCop() {
    return unitPriceCop;
  }

  public void setUnitPriceCop(BigDecimal unitPriceCop) {
    this.unitPriceCop = unitPriceCop != null ? unitPriceCop : BigDecimal.ZERO;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }

  @Override
  public String toString() {
    return "InvoiceItem{"
        + "id=" + getId()
        + ", description='" + description + '\''
        + ", quantity=" + quantity
        + '}';
  }
}
