package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.InvoiceItem;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Salida de un ítem de factura (FASE4-04). Incluye el total calculado por
 * la BD (columna generada).
 */
public class InvoiceItemResponse {

  private final UUID id;
  private final String description;
  private final int quantity;
  private final BigDecimal unitPriceCop;
  private final BigDecimal totalCop;

  public InvoiceItemResponse(
      UUID id, String description, int quantity, BigDecimal unitPriceCop, BigDecimal totalCop) {
    this.id = id;
    this.description = description;
    this.quantity = quantity;
    this.unitPriceCop = unitPriceCop;
    this.totalCop = totalCop;
  }

  public static InvoiceItemResponse fromEntity(InvoiceItem item) {
    return new InvoiceItemResponse(
        item.getId(),
        item.getDescription(),
        item.getQuantity(),
        item.getUnitPriceCop(),
        item.getTotalCop());
  }

  public UUID getId() {
    return id;
  }

  public String getDescription() {
    return description;
  }

  public int getQuantity() {
    return quantity;
  }

  public BigDecimal getUnitPriceCop() {
    return unitPriceCop;
  }

  public BigDecimal getTotalCop() {
    return totalCop;
  }
}
