package com.julio.odentix.odentix_backend.audit.entity;

/**
 * Acciones auditables (FASE1-13).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL `audit_action`
 * (docs/schema.sql §2): 'insert', 'update', 'delete', 'login_success', 'login_failed'.
 */
public enum AuditAction {
  insert,
  update,
  delete,
  login_success,
  login_failed
}
