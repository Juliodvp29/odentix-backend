package com.julio.odentix.odentix_backend.opportunity.entity;

/**
 * Estados del ciclo de vida de una oportunidad (FASE9-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code opportunity_status}
 * definido en {@code V23__create_opportunities.sql} y en {@code docs/schema.sql §2}.
 */
public enum OpportunityStatus {
  abierta,
  en_progreso,
  resuelta,
  descartada
}
