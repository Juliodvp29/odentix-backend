package com.julio.odentix.odentix_backend.saas.dto;

import java.util.List;
import java.util.Map;

/**
 * Resumen del plan del tenant activo para gating en el frontend.
 *
 * <p>Lleva el código del plan, los features habilitados y los límites
 * numéricos (solo valores no nulos: ausente significa ilimitado).
 * Con {@code planCode} nulo no hay suscripción viva y el gating queda
 * inactivo (fail-open pre-billing, igual que `requireFeature`). Sin Lombok.
 */
public class PlanSummaryResponse {

  private String planCode;
  private List<String> features;
  private Map<String, Integer> limits;

  public PlanSummaryResponse() {
  }

  public PlanSummaryResponse(String planCode, List<String> features, Map<String, Integer> limits) {
    this.planCode = planCode;
    this.features = features;
    this.limits = limits;
  }

  public String getPlanCode() {
    return planCode;
  }

  public void setPlanCode(String planCode) {
    this.planCode = planCode;
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
