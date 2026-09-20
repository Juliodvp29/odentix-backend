package com.julio.odentix.odentix_backend.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Respuesta consolidada de métricas de conversión comercial en el CRM (FASE5-04).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class LeadConversionMetricsResponse {

  private Instant from;
  private Instant to;
  private long totalLeads;
  private long convertedLeads;
  private BigDecimal conversionRatePercentage;
  private List<ConversionMetricItem> bySource = new ArrayList<>();
  private List<ConversionMetricItem> byCampaign = new ArrayList<>();

  public LeadConversionMetricsResponse() {
  }

  public LeadConversionMetricsResponse(
      Instant from,
      Instant to,
      long totalLeads,
      long convertedLeads,
      BigDecimal conversionRatePercentage,
      List<ConversionMetricItem> bySource,
      List<ConversionMetricItem> byCampaign) {
    this.from = from;
    this.to = to;
    this.totalLeads = totalLeads;
    this.convertedLeads = convertedLeads;
    this.conversionRatePercentage = conversionRatePercentage;
    this.bySource = bySource != null ? bySource : new ArrayList<>();
    this.byCampaign = byCampaign != null ? byCampaign : new ArrayList<>();
  }

  public Instant getFrom() {
    return from;
  }

  public void setFrom(Instant from) {
    this.from = from;
  }

  public Instant getTo() {
    return to;
  }

  public void setTo(Instant to) {
    this.to = to;
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

  public List<ConversionMetricItem> getBySource() {
    return bySource;
  }

  public void setBySource(List<ConversionMetricItem> bySource) {
    this.bySource = bySource;
  }

  public List<ConversionMetricItem> getByCampaign() {
    return byCampaign;
  }

  public void setByCampaign(List<ConversionMetricItem> byCampaign) {
    this.byCampaign = byCampaign;
  }
}

