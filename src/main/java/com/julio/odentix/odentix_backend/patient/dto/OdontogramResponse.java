package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Odontograma de un paciente agrupado por pieza y por tipo (FASE2-08).
 *
 * <p>Cada pieza incluye siempre los cuatro tipos
 * ({@code estado_actual}, {@code diagnostico}, {@code plan_propuesto},
 * {@code tratamiento_realizado}), con lista vacía si no hay entradas: el
 * consumidor nunca tiene que inferir qué falta. Las entradas de cada tipo
 * van ordenadas por fecha clínica descendente.
 */
public class OdontogramResponse {

  private UUID patientId;
  private List<ToothGroup> teeth;

  public OdontogramResponse(UUID patientId, List<ToothGroup> teeth) {
    this.patientId = patientId;
    this.teeth = teeth;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public List<ToothGroup> getTeeth() {
    return teeth;
  }

  /**
   * Entradas de una pieza dental separadas por tipo.
   */
  public static class ToothGroup {

    private short toothNumber;
    private Map<OdontogramEntryType, List<OdontogramEntryResponse>> entries;

    public ToothGroup(short toothNumber, Map<OdontogramEntryType, List<OdontogramEntryResponse>> entries) {
      this.toothNumber = toothNumber;
      this.entries = entries;
    }

    public short getToothNumber() {
      return toothNumber;
    }

    public Map<OdontogramEntryType, List<OdontogramEntryResponse>> getEntries() {
      return entries;
    }
  }
}
