package com.julio.odentix.odentix_backend.crm.dto;

import java.math.BigDecimal;

/**
 * Métrica de conversión agrupada por una dimensión comercial (fuente o campaña) (FASE5-04).
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
public class ConversionMetricItem {

  private String dimensionValue;
  private long totalLeads;
  private long convertedLeads;
  private BigDecimal conversionRatePercentage;

  public ConversionMetricItem() {
  }

  public ConversionMetricItem(
      String dimensionValue,
      long totalLeads,
      long convertedLeads,
      BigDecimal conversionRatePercentage) {
    this.dimensionValue = dimensionValue;
    this.totalLeads = totalLeads;
    this.convertedLeads = convertedLeads;
    this.conversionRatePercentage = conversionRatePercentage;
  }

  public String getDimensionValue() {
    return dimensionValue;
  }

  public void setDimensionValue(String dimensionValue) {
    this.dimensionValue = dimensionValue;
  }

  public long getTotalLeads() {
    return totalLeads;
  }

  public void setTotalLeads(long totalLeads) {
    this.totalLeads = totalLeads;
  }

  public long getConvertedLeads() {
    return convertedLeads;
  }

  public void setConvertedLeads(long convertedLeads) {
    this.convertedLeads = convertedLeads;
  }

  public BigDecimal getConversionRatePercentage() {
    return conversionRatePercentage;
  }

  public void setConversionRatePercentage(BigDecimal conversionRatePercentage) {
    this.conversionRatePercentage = conversionRatePercentage;
  }
}
