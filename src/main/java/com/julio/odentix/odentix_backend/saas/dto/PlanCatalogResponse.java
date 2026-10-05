package com.julio.odentix.odentix_backend.saas.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Plan del catálogo con su detalle completo para la pantalla de planes.
 *
 * <p>Todo sale de la BD (`plans`, `plan_features`, `plan_limits`): el
 * frontend nunca hardcodea precios, features ni límites. Solo valores no
 * nulos en límites (ausente significa ilimitado). Sin Lombok.
 */
public class PlanCatalogResponse {

  private String code;
  private String name;
  private BigDecimal monthlyPriceCop;
  private BigDecimal annualPriceCop;
  private List<String> features;
  private Map<String, Integer> limits;

  public PlanCatalogResponse() {
  }

  public PlanCatalogResponse(
      String code, String name, BigDecimal monthlyPriceCop, BigDecimal annualPriceCop,
      List<String> features, Map<String, Integer> limits) {
    this.code = code;
    this.name = name;
    this.monthlyPriceCop = monthlyPriceCop;
    this.annualPriceCop = annualPriceCop;
    this.features = features;
    this.limits = limits;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public BigDecimal getMonthlyPriceCop() {
    return monthlyPriceCop;
  }

  public void setMonthlyPriceCop(BigDecimal monthlyPriceCop) {
    this.monthlyPriceCop = monthlyPriceCop;
  }

  public BigDecimal getAnnualPriceCop() {
    return annualPriceCop;
  }

  public void setAnnualPriceCop(BigDecimal annualPriceCop) {
    this.annualPriceCop = annualPriceCop;
  }

  public List<String> getFeatures() {
    return features;
  }

  public void setFeatures(List<String> features) {
    this.features = features;
  }

  public Map<String, Integer> getLimits() {
    return limits;
  }

  public void setLimits(Map<String, Integer> limits) {
    this.limits = limits;
  }
}
