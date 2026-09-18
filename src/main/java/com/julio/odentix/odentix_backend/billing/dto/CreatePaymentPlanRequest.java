package com.julio.odentix.odentix_backend.billing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Cuerpo de la request para crear un plan de pago en cuotas (FASE6-02).
 *
 * <p>El servicio distribuye {@code totalAmountCop} entre {@code installmentsCount}
 * cuotas mensuales comenzando desde hoy. El monto de la última cuota absorbe
 * el resto de redondeo para garantizar que la suma cierre.
 */
public class CreatePaymentPlanRequest {

  /**
   * Monto total pactado del plan de pago. No tiene que coincidir exactamente
   * con el total del tratamiento (puede ser un saldo pendiente, o un monto
   * acordado diferente al total facturado).
   */
  @NotNull(message = "El monto total es obligatorio")
  @DecimalMin(value = "0.01", message = "El monto total debe ser mayor a cero")
  private BigDecimal totalAmountCop;

  /**
   * Número de cuotas en que se divide el pago. Mínimo 1 (pago único diferido),
   * máximo 60 (5 años) como límite razonable de plataforma.
   */
  @NotNull(message = "El número de cuotas es obligatorio")
  @Min(value = 1, message = "Debe haber al menos 1 cuota")
  @Max(value = 60, message = "El número de cuotas no puede exceder 60")
  private Integer installmentsCount;

  public BigDecimal getTotalAmountCop() {
    return totalAmountCop;
  }

  public void setTotalAmountCop(BigDecimal totalAmountCop) {
    this.totalAmountCop = totalAmountCop;
  }

  public Integer getInstallmentsCount() {
    return installmentsCount;
  }

  public void setInstallmentsCount(Integer installmentsCount) {
    this.installmentsCount = installmentsCount;
  }
}
