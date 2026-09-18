package com.julio.odentix.odentix_backend.billing.dto;

import com.julio.odentix.odentix_backend.billing.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Petición de registro de pago contra una factura (FASE4-04).
 */
public class CreatePaymentRequest {

  @NotNull(message = "El monto es obligatorio")
  @Positive(message = "El monto debe ser mayor que cero")
  private BigDecimal amountCop;

  @NotNull(message = "El medio de pago es obligatorio")
  private PaymentMethod method;

  private String reference;

  public CreatePaymentRequest() {
  }

  public CreatePaymentRequest(BigDecimal amountCop, PaymentMethod method) {
    this.amountCop = amountCop;
    this.method = method;
  }

  public BigDecimal getAmountCop() {
    return amountCop;
  }

  public void setAmountCop(BigDecimal amountCop) {
    this.amountCop = amountCop;
  }

  public PaymentMethod getMethod() {
    return method;
  }

  public void setMethod(PaymentMethod method) {
    this.method = method;
  }

  public String getReference() {
    return reference;
  }

  public void setReference(String reference) {
    this.reference = reference;
  }
}
