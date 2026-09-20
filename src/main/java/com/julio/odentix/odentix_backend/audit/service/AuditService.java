package com.julio.odentix.odentix_backend.audit.service;

import com.julio.odentix.odentix_backend.audit.entity.AuditAction;
import com.julio.odentix.odentix_backend.audit.entity.AuditLog;
import com.julio.odentix.odentix_backend.audit.repository.AuditRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio simple de auditoría reutilizable desde cualquier módulo (FASE1-13).
 *
 * <p>Solo escribe (append-only): no expone actualización ni borrado.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Service
public class AuditService {

  private final AuditRepository auditRepository;

  public AuditService(AuditRepository auditRepository) {
    this.auditRepository = auditRepository;
  }

  /**
   * Registra una entrada de auditoría.
   *
   * @param tenantId tenant al que pertenece el evento (obligatorio)
   * @param userId usuario que originó el evento (anulable: hay eventos sin usuario)
   * @param action acción auditada (obligatoria)
   * @param entityName entidad afectada, ej. "users" (obligatoria)
   * @param entityId id de la entidad afectada (anulable)
   * @param detail detalle libre JSON (anulable; nunca incluir contraseñas)
   * @throws IllegalArgumentException si falta tenant, acción o entidad
   */
  // REQUIRES_NEW (FASE1-14): la auditoría debe persistir aunque la transacción
  // que la originó haga rollback (ej. un login fallido lanza excepción y su
  // transacción se revierte; sin esto, el registro del fallo se perdería).
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public AuditLog log(
      UUID tenantId,
      UUID userId,
      AuditAction action,
      String entityName,
      UUID entityId,
      Map<String, Object> detail) {
    if (tenantId == null || action == null || entityName == null || entityName.isBlank()) {
      throw new IllegalArgumentException("tenant, acción y entidad son obligatorios");
    }
    AuditLog entry = new AuditLog(tenantId, userId, action, entityName, entityId, detail);
    return auditRepository.save(entry);
  }
}

