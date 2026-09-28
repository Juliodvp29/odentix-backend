package com.julio.odentix.odentix_backend.specialist.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Solicitud para crear la ficha financiera de un especialista externo.
 *
 * <p>El profesional debe existir en el tenant activo y ser externo
 * ({@code isExternal = true}); un profesional solo puede tener una ficha
 * (segundo intento → 409).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class CreateSpecialistRequest {

  @NotNull(message = "professionalId es obligatorio")
  private UUID professionalId;

  @NotNull(message = "feePercentage es obligatorio")
  @DecimalMin(value = "0.00", message = "feePercentage debe estar entre 0 y 100")
  @DecimalMax(value = "100.00", message = "feePercentage debe estar entre 0 y 100")
  private BigDecimal feePercentage;

  private String paymentTerms;

  public CreateSpecialistRequest() {
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }

  public BigDecimal getFeePercentage() {
    return feePercentage;
  }

  public void setFeePercentage(BigDecimal feePercentage) {
    this.feePercentage = feePercentage;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
  }
}
