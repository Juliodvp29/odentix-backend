package com.julio.odentix.odentix_backend.crm.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Métrica de velocidad de atención y tiempo de primera respuesta a leads (FASE5-04).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class LeadResponseTimeMetricsResponse {

  private Instant from;
  private Instant to;
  private long totalLeads;
  private long respondedLeads;
  private long unrespondedLeads;
  private BigDecimal responseRatePercentage;
  private Double averageResponseTimeMinutes;
  private Double averageResponseTimeHours;

  public LeadResponseTimeMetricsResponse() {
  }

  public LeadResponseTimeMetricsResponse(
      Instant from,
      Instant to,
      long totalLeads,
      long respondedLeads,
      long unrespondedLeads,
      BigDecimal responseRatePercentage,
      Double averageResponseTimeMinutes,
      Double averageResponseTimeHours) {
    this.from = from;
    this.to = to;
    this.totalLeads = totalLeads;
    this.respondedLeads = respondedLeads;
    this.unrespondedLeads = unrespondedLeads;
    this.responseRatePercentage = responseRatePercentage;
    this.averageResponseTimeMinutes = averageResponseTimeMinutes;
    this.averageResponseTimeHours = averageResponseTimeHours;
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

  public long getRespondedLeads() {
    return respondedLeads;
  }

  public void setRespondedLeads(long respondedLeads) {
    this.respondedLeads = respondedLeads;
  }

  public long getUnrespondedLeads() {
    return unrespondedLeads;
  }

  public void setUnrespondedLeads(long unrespondedLeads) {
    this.unrespondedLeads = unrespondedLeads;
  }

  public BigDecimal getResponseRatePercentage() {
    return responseRatePercentage;
  }

  public void setResponseRatePercentage(BigDecimal responseRatePercentage) {
    this.responseRatePercentage = responseRatePercentage;
  }

  public Double getAverageResponseTimeMinutes() {
    return averageResponseTimeMinutes;
  }

  public void setAverageResponseTimeMinutes(Double averageResponseTimeMinutes) {
    this.averageResponseTimeMinutes = averageResponseTimeMinutes;
  }

  public Double getAverageResponseTimeHours() {
    return averageResponseTimeHours;
  }

  public void setAverageResponseTimeHours(Double averageResponseTimeHours) {
    this.averageResponseTimeHours = averageResponseTimeHours;
  }
}

