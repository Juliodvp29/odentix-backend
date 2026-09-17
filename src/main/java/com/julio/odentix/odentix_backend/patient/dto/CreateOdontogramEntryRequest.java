package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/**
 * Entrada para registrar un apunte de odontograma (FASE2-08).
 *
 * <p>Clase explícita sin Lombok, como el resto de DTOs del módulo. El autor
 * ({@code recordedBy}) no se acepta del cliente: queda {@code null} hasta
 * que existan profesionales en Fase 3.
 */
public class CreateOdontogramEntryRequest {

  @NotNull(message = "El número de pieza dental es obligatorio")
  @FdiToothNumber
  private Integer toothNumber;

  private String surface;

  @NotNull(message = "El tipo de entrada es obligatorio")
  private OdontogramEntryType entryType;

  @NotBlank(message = "La condición no puede estar vacía")
  private String condition;

  private String notes;

  private Instant recordedAt;

  public Integer getToothNumber() {
    return toothNumber;
  }

  public void setToothNumber(Integer toothNumber) {
    this.toothNumber = toothNumber;
  }

  public String getSurface() {
    return surface;
  }

  public void setSurface(String surface) {
    this.surface = surface;
  }

  public OdontogramEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(OdontogramEntryType entryType) {
    this.entryType = entryType;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }
}
