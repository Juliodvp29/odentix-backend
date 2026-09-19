package com.julio.odentix.odentix_backend.opportunity.entity;

/**
 * Tipos de oportunidad detectados por el motor de reglas (FASE9-01).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL {@code opportunity_type}
 * definido en {@code V23__create_opportunities.sql} y en {@code docs/schema.sql §2}.
 *
 * <p>Cada valor mapea a una regla de detección específica:
 * <ul>
 *   <li>{@link #tratamiento_sin_seguimiento} — FASE9-01: plan presentado/en decisión sin contacto reciente.</li>
 *   <li>{@link #lead_sin_respuesta} — FASE9-02: lead sin actividad de respuesta.</li>
 *   <li>{@link #cita_alto_riesgo} — FASE9-02: cita con risk_level elevado.</li>
 *   <li>{@link #espacio_disponible} — FASE9-02: hueco en agenda aprovechable.</li>
 *   <li>{@link #paciente_inactivo} — FASE9-02: paciente sin citas ni tratamientos en X tiempo.</li>
 *   <li>{@link #saldo_vencido} — FASE9-02: cuotas vencidas sin cobrar.</li>
 *   <li>{@link #inventario_critico} — FASE9-02: stock por debajo del mínimo.</li>
 * </ul>
 */
public enum OpportunityType {
  lead_sin_respuesta,
  tratamiento_sin_seguimiento,
  cita_alto_riesgo,
  espacio_disponible,
  paciente_inactivo,
  saldo_vencido,
  inventario_critico
}
