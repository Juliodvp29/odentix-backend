package com.julio.odentix.odentix_backend.crm.service;

import com.julio.odentix.odentix_backend.crm.dto.ConversionMetricItem;
import com.julio.odentix.odentix_backend.crm.dto.LeadConversionMetricsResponse;
import com.julio.odentix.odentix_backend.crm.dto.LeadResponseTimeMetricsResponse;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de analítica y agregación comercial del CRM (FASE5-04).
 *
 * <p>Calcula tasas de conversión por canal y campaña, así como velocidad
 * y tiempos de respuesta inicial a prospectos para el dashboard de gestión.
 *
 * <p>Aislamiento multi-tenant estricto: todas las consultas
 * filtran obligatoriamente por el {@code tenant_id} activo en {@link TenantContext}.
 */
@Service
public class LeadMetricsService {

  private final EntityManager entityManager;

  public LeadMetricsService(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  /**
   * Calcula las métricas de conversión comercial (global, por fuente y por campaña)
   * para los prospectos creados dentro del rango temporal especificado.
   *
   * @param from inicio opcional del período de creación.
   * @param to fin opcional del período de creación.
   * @return resumen de conversión comercial.
   */
  @Transactional(readOnly = true)
  public LeadConversionMetricsResponse getConversionMetrics(Instant from, Instant to) {
    validateDateRange(from, to);
    UUID tenantId = TenantContext.getTenantId();

    List<ConversionMetricItem> bySource = calculateConversionByDimension(
        tenantId, from, to, "COALESCE(l.source, 'desconocido')");

    List<ConversionMetricItem> byCampaign = calculateConversionByDimension(
        tenantId, from, to, "COALESCE(l.campaign, 'sin_campana')");

    long totalLeads = 0;
    long convertedLeads = 0;

    for (ConversionMetricItem item : bySource) {
      totalLeads += item.getTotalLeads();
      convertedLeads += item.getConvertedLeads();
    }

    BigDecimal globalRate = calculateRate(convertedLeads, totalLeads);

    return new LeadConversionMetricsResponse(
        from,
        to,
        totalLeads,
        convertedLeads,
        globalRate,
        bySource,
        byCampaign
    );
  }

  /**
   * Calcula las métricas de velocidad y tiempo promedio hasta la primera respuesta
   * para los prospectos creados dentro del rango temporal especificado.
   *
   * @param from inicio opcional del período de creación.
   * @param to fin opcional del período de creación.
   * @return métricas de tiempo de primera respuesta.
   */
  @Transactional(readOnly = true)
  public LeadResponseTimeMetricsResponse getResponseTimeMetrics(Instant from, Instant to) {
    validateDateRange(from, to);
    UUID tenantId = TenantContext.getTenantId();

    StringBuilder jpql = new StringBuilder(
        "SELECT l.id, l.createdAt, MIN(la.createdAt) " +
        "FROM Lead l " +
        "LEFT JOIN LeadActivity la ON la.lead.id = l.id AND la.tenantId = l.tenantId " +
        "WHERE l.tenantId = :tenantId "
    );

    if (from != null) {
      jpql.append("AND l.createdAt >= :from ");
    }
    if (to != null) {
      jpql.append("AND l.createdAt <= :to ");
    }
    jpql.append("GROUP BY l.id, l.createdAt");

    TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
    query.setParameter("tenantId", tenantId);
    if (from != null) {
      query.setParameter("from", from);
    }
    if (to != null) {
      query.setParameter("to", to);
    }

    List<Object[]> rows = query.getResultList();

    long respondedLeads = 0;
    long unrespondedLeads = 0;
    long totalMinutes = 0;

    for (Object[] row : rows) {
      Instant leadCreatedAt = (Instant) row[1];
      Instant firstActivityAt = (Instant) row[2];

      if (firstActivityAt != null) {
        respondedLeads++;
        long minutes = Duration.between(leadCreatedAt, firstActivityAt).toMinutes();
        if (minutes < 0) {
          minutes = 0;
        }
        totalMinutes += minutes;
      } else {
        unrespondedLeads++;
      }
    }

    long totalLeads = respondedLeads + unrespondedLeads;
    BigDecimal responseRate = calculateRate(respondedLeads, totalLeads);

    Double avgMinutes = null;
    Double avgHours = null;

    if (respondedLeads > 0) {
      double rawAvgMinutes = (double) totalMinutes / respondedLeads;
      avgMinutes = BigDecimal.valueOf(rawAvgMinutes).setScale(2, RoundingMode.HALF_UP).doubleValue();
      avgHours = BigDecimal.valueOf(rawAvgMinutes / 60.0).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    return new LeadResponseTimeMetricsResponse(
        from,
        to,
        totalLeads,
        respondedLeads,
        unrespondedLeads,
        responseRate,
        avgMinutes,
        avgHours
    );
  }

  private List<ConversionMetricItem> calculateConversionByDimension(
      UUID tenantId, Instant from, Instant to, String dimensionExpression) {

    StringBuilder jpql = new StringBuilder("SELECT ")
        .append(dimensionExpression)
        .append(", COUNT(l.id), ")
        .append("SUM(CASE WHEN l.convertedPatient IS NOT NULL THEN 1L ELSE 0L END) ")
        .append("FROM Lead l WHERE l.tenantId = :tenantId ");

    if (from != null) {
      jpql.append("AND l.createdAt >= :from ");
    }
    if (to != null) {
      jpql.append("AND l.createdAt <= :to ");
    }
    jpql.append("GROUP BY ").append(dimensionExpression).append(" ORDER BY COUNT(l.id) DESC");

    TypedQuery<Object[]> query = entityManager.createQuery(jpql.toString(), Object[].class);
    query.setParameter("tenantId", tenantId);
    if (from != null) {
      query.setParameter("from", from);
    }
    if (to != null) {
      query.setParameter("to", to);
    }

    List<Object[]> rows = query.getResultList();
    List<ConversionMetricItem> items = new ArrayList<>();

    for (Object[] row : rows) {
      String dimensionValue = (String) row[0];
      long total = ((Number) row[1]).longValue();
      long converted = ((Number) row[2]).longValue();
      BigDecimal rate = calculateRate(converted, total);

      items.add(new ConversionMetricItem(dimensionValue, total, converted, rate));
    }

    return items;
  }

  private BigDecimal calculateRate(long count, long total) {
    if (total == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return BigDecimal.valueOf((count * 100.0) / total).setScale(2, RoundingMode.HALF_UP);
  }

  private void validateDateRange(Instant from, Instant to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'");
    }
  }
}

