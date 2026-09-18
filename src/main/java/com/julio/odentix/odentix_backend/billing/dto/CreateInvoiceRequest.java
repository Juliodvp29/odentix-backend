package com.julio.odentix.odentix_backend.billing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Petición de generación de factura (FASE4-04).
 *
 * <p>Dos modos excluyentes: desde un plan de tratamiento (ítems y paciente
 * inferidos) o manual (paciente + ítems explícitos). El servicio rechaza
 * mezclar ambos o un paciente distinto al del plan.
 */
public class CreateInvoiceRequest {

  private UUID patientId;

  private UUID treatmentPlanId;

  @PositiveOrZero(message = "El descuento no puede ser negativo")
  private BigDecimal discountCop;

  @Size(min = 1, message = "La factura manual requiere al menos un ítem")
  private List<@Valid InvoiceItemInput> items;

  public CreateInvoiceRequest() {
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getTreatmentPlanId() {
    return treatmentPlanId;
  }

  public void setTreatmentPlanId(UUID treatmentPlanId) {
    this.treatmentPlanId = treatmentPlanId;
  }

  public BigDecimal getDiscountCop() {
    return discountCop;
  }

  public void setDiscountCop(BigDecimal discountCop) {
    this.discountCop = discountCop;
  }

  public List<InvoiceItemInput> getItems() {
    return items;
  }

  public void setItems(List<InvoiceItemInput> items) {
    this.items = items;
  }
}
